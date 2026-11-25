package com.flowerchain.payment.gateway;

import com.flowerchain.shared.api.FlowerChainException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Sandbox payment provider adapter (ADR-0007): HMAC-SHA256 over the raw body with the secret
 * {@value #SECRET_PROPERTY}. See {@link HmacSha256WebhookSignatureVerifier} for the exact signing scheme. Real
 * provider adapters (B4) plug into {@link PaymentGatewayPort} next to this one.
 *
 * <p>Off unless {@value #ENABLED_PROPERTY} is {@code true} (security review F2). When off, the bean does not exist
 * and {@code sandbox-hmac} is rejected like any unsupported provider.
 */
@Component
@ConditionalOnBooleanProperty(name = SandboxHmacPaymentGateway.ENABLED_PROPERTY)
public class SandboxHmacPaymentGateway implements PaymentGatewayPort {

    public static final String PROVIDER_CODE = "sandbox-hmac";
    public static final String ENABLED_PROPERTY = "flowerchain.payment.sandbox-hmac.enabled";
    public static final String SECRET_PROPERTY = "flowerchain.payment.sandbox-hmac.webhook-secret";
    public static final String SIGNATURE_HEADER = "X-Payment-Signature";
    public static final String TIMESTAMP_HEADER = "X-Payment-Timestamp";

    private static final Logger log = LoggerFactory.getLogger(SandboxHmacPaymentGateway.class);

    private static final int MAX_ID_LENGTH = 128;
    /** Provider IDs are logged and audited, so only a safe character set is accepted (no CR/LF, quotes, ...). */
    private static final Pattern PROVIDER_ID = Pattern.compile("[A-Za-z0-9._:\\-]{1,128}");
    private static final Pattern AMOUNT = Pattern.compile("[0-9]{1,17}(\\.[0-9]{1,2})?");
    private static final Pattern CURRENCY_CODE = Pattern.compile("[A-Z]{3}");

    private final WebhookSignatureVerifier verifier;
    private final ObjectMapper objectMapper;

    public SandboxHmacPaymentGateway(
            @Value("${" + SECRET_PROPERTY + ":}") String webhookSecret,
            Clock clock,
            ObjectMapper objectMapper) {
        this.verifier = new HmacSha256WebhookSignatureVerifier(webhookSecret, clock);
        this.objectMapper = objectMapper;
        if (!verifier.isConfigured()) {
            // Logged once, at startup. Never log the secret itself (or its length).
            log.warn("Payment webhook secret '{}' is blank, a placeholder, or shorter than {} characters. Every "
                            + "'{}' webhook will be rejected with 503 until FLOWERCHAIN_PAYMENT_SANDBOX_HMAC_WEBHOOK_SECRET "
                            + "is set to a strong secret.",
                    SECRET_PROPERTY,
                    HmacSha256WebhookSignatureVerifier.MIN_SECRET_LENGTH,
                    PROVIDER_CODE);
        }
    }

    @Override
    public String providerCode() {
        return PROVIDER_CODE;
    }

    @Override
    public VerifiedPaymentEvent verifyAndParse(byte[] rawBody, HttpHeaders headers) {
        WebhookSignatureVerifier.Result result = verifier.verify(
                rawBody, headers.getFirst(SIGNATURE_HEADER), headers.getFirst(TIMESTAMP_HEADER));
        if (result == WebhookSignatureVerifier.Result.SECRET_NOT_CONFIGURED) {
            throw new FlowerChainException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "PAYMENT_WEBHOOK_NOT_CONFIGURED",
                    "Payment webhook unavailable",
                    "Payment webhooks cannot be accepted because the server is not configured for this provider.");
        }
        if (result != WebhookSignatureVerifier.Result.VALID) {
            // The reason stays in the server log; the response is the same for every failure.
            log.warn("Rejected '{}' payment webhook: {} ({} body bytes).",
                    PROVIDER_CODE, result, rawBody == null ? 0 : rawBody.length);
            throw FlowerChainException.unauthorized(
                    "PAYMENT_WEBHOOK_SIGNATURE_INVALID", "The webhook signature is missing or invalid.");
        }
        return parseVerifiedBody(rawBody);
    }

    private VerifiedPaymentEvent parseVerifiedBody(byte[] rawBody) {
        JsonNode root;
        try {
            root = objectMapper.readTree(rawBody);
        } catch (JacksonException e) {
            throw invalidPayload("the body is not valid JSON");
        }
        if (root == null || !root.isObject()) {
            throw invalidPayload("the body is not a JSON object");
        }

        String eventId = providerId(root, "eventId");
        String transactionId = providerId(root, "transactionId");
        UUID orderId = parseUuid(requiredString(root, "orderId", 36));
        VerifiedPaymentEvent.Status status = parseStatus(requiredString(root, "status", 16));
        BigDecimal amount = parseAmount(requiredString(root, "amount", 21));
        String currencyCode = requiredString(root, "currencyCode", 3);
        if (!CURRENCY_CODE.matcher(currencyCode).matches()) {
            throw invalidPayload("field 'currencyCode' must be a 3-letter ISO 4217 code");
        }
        Instant occurredAt = parseInstant(requiredString(root, "occurredAt", 64));

        return new VerifiedPaymentEvent(
                PROVIDER_CODE, eventId, transactionId, orderId, status, amount, currencyCode, occurredAt);
    }

    private static String requiredString(JsonNode root, String field, int maxLength) {
        JsonNode node = root.get(field);
        if (node == null || !node.isString()) {
            throw invalidPayload("field '" + field + "' must be a string");
        }
        String value = node.stringValue();
        if (value.isBlank() || value.length() > maxLength) {
            throw invalidPayload("field '" + field + "' must be 1 to " + maxLength + " characters");
        }
        return value;
    }

    private static String providerId(JsonNode root, String field) {
        String value = requiredString(root, field, MAX_ID_LENGTH);
        if (!PROVIDER_ID.matcher(value).matches()) {
            throw invalidPayload("field '" + field + "' may only contain letters, digits and . _ : -");
        }
        return value;
    }

    private static UUID parseUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            throw invalidPayload("field 'orderId' must be a UUID");
        }
    }

    private static VerifiedPaymentEvent.Status parseStatus(String value) {
        return switch (value) {
            case "CAPTURED" -> VerifiedPaymentEvent.Status.CAPTURED;
            case "FAILED" -> VerifiedPaymentEvent.Status.FAILED;
            default -> throw invalidPayload("field 'status' must be CAPTURED or FAILED");
        };
    }

    private static BigDecimal parseAmount(String value) {
        if (!AMOUNT.matcher(value).matches()) {
            throw invalidPayload("field 'amount' must be a non-negative decimal with at most 2 decimal places");
        }
        return new BigDecimal(value);
    }

    private static Instant parseInstant(String value) {
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException e) {
            throw invalidPayload("field 'occurredAt' must be an ISO-8601 timestamp");
        }
    }

    private static FlowerChainException invalidPayload(String reason) {
        return FlowerChainException.badRequest(
                "PAYMENT_WEBHOOK_PAYLOAD_INVALID", "The verified webhook payload is invalid: " + reason + ".");
    }
}
