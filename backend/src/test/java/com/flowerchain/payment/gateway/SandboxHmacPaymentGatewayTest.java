package com.flowerchain.payment.gateway;

import static com.flowerchain.payment.WebhookTestSignatures.OTHER_SECRET;
import static com.flowerchain.payment.WebhookTestSignatures.SECRET;
import static com.flowerchain.payment.WebhookTestSignatures.payload;
import static com.flowerchain.payment.WebhookTestSignatures.sign;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.flowerchain.shared.api.FlowerChainException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/** TEST-011 (unit level): verify-before-parse, fail-closed secret, payload validation (REQ-009). */
class SandboxHmacPaymentGatewayTest {

    private static final Instant NOW = Instant.parse("2026-11-25T08:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final UUID ORDER_ID = UUID.fromString("7d2c9a4e-3f1b-4c8d-9e0f-112233445566");

    private final ObjectMapper objectMapper = JsonMapper.builder().build();
    private final Logger gatewayLogger = (Logger) LoggerFactory.getLogger(SandboxHmacPaymentGateway.class);
    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();

    @BeforeEach
    void attachLogCapture() {
        logs.start();
        gatewayLogger.addAppender(logs);
    }

    @AfterEach
    void detachLogCapture() {
        gatewayLogger.detachAppender(logs);
    }

    @Test
    void validSignatureYieldsParsedEvent() {
        SandboxHmacPaymentGateway gateway = gateway(SECRET);
        byte[] body = payload("evt_1001", "txn_9001", ORDER_ID, "CAPTURED", "500000.00", "VND");

        VerifiedPaymentEvent event = gateway.verifyAndParse(body, headers(sign(SECRET, body), null));

        assertThat(event.provider()).isEqualTo("sandbox-hmac");
        assertThat(event.eventId()).isEqualTo("evt_1001");
        assertThat(event.transactionId()).isEqualTo("txn_9001");
        assertThat(event.orderId()).isEqualTo(ORDER_ID);
        assertThat(event.status()).isEqualTo(VerifiedPaymentEvent.Status.CAPTURED);
        assertThat(event.amount()).isEqualByComparingTo(new BigDecimal("500000"));
        assertThat(event.currencyCode()).isEqualTo("VND");
        assertThat(event.occurredAt()).isEqualTo(Instant.parse("2026-11-25T07:59:00Z"));
    }

    @Test
    void timestampedSignatureIsAccepted() {
        SandboxHmacPaymentGateway gateway = gateway(SECRET);
        byte[] body = payload("evt_1002", "txn_9002", ORDER_ID, "FAILED", "10", "VND");
        String timestamp = Long.toString(NOW.getEpochSecond());

        VerifiedPaymentEvent event = gateway.verifyAndParse(body, headers(sign(SECRET, timestamp, body), timestamp));

        assertThat(event.status()).isEqualTo(VerifiedPaymentEvent.Status.FAILED);
    }

    @Test
    void forgedSignatureIsRejectedWith401() {
        SandboxHmacPaymentGateway gateway = gateway(SECRET);
        byte[] body = payload("evt_1001", "txn_9001", ORDER_ID, "CAPTURED", "500000", "VND");

        assertStatus(() -> gateway.verifyAndParse(body, headers(sign(OTHER_SECRET, body), null)),
                HttpStatus.UNAUTHORIZED, "PAYMENT_WEBHOOK_SIGNATURE_INVALID");
        assertStatus(() -> gateway.verifyAndParse(body, headers(null, null)),
                HttpStatus.UNAUTHORIZED, "PAYMENT_WEBHOOK_SIGNATURE_INVALID");
    }

    @Test
    void signatureIsCheckedBeforeTheBodyIsParsed() {
        SandboxHmacPaymentGateway gateway = gateway(SECRET);
        byte[] notJson = "this is not json".getBytes(StandardCharsets.UTF_8);

        // Unsigned garbage gets 401, not 400: the parser never sees an unverified body.
        assertStatus(() -> gateway.verifyAndParse(notJson, headers("00".repeat(32), null)),
                HttpStatus.UNAUTHORIZED, "PAYMENT_WEBHOOK_SIGNATURE_INVALID");
        // The same garbage, correctly signed, reaches the parser and gets 400.
        assertStatus(() -> gateway.verifyAndParse(notJson, headers(sign(SECRET, notJson), null)),
                HttpStatus.BAD_REQUEST, "PAYMENT_WEBHOOK_PAYLOAD_INVALID");
    }

    @Test
    void rejectionLogNamesTheReasonButNeverTheSignatureSecretOrBody() {
        SandboxHmacPaymentGateway gateway = gateway(SECRET);
        byte[] body = payload("evt_secret_body_marker", "txn_9001", ORDER_ID, "CAPTURED", "500000", "VND");
        String forged = sign(OTHER_SECRET, body);

        assertStatus(() -> gateway.verifyAndParse(body, headers(forged, null)),
                HttpStatus.UNAUTHORIZED, "PAYMENT_WEBHOOK_SIGNATURE_INVALID");

        assertThat(logs.list).hasSize(1);
        ILoggingEvent warning = logs.list.get(0);
        assertThat(warning.getLevel()).isEqualTo(Level.WARN);
        assertThat(warning.getFormattedMessage())
                .contains("SIGNATURE_MISMATCH")
                .doesNotContain(forged, SECRET, OTHER_SECRET, "evt_secret_body_marker");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   ", HmacSha256WebhookSignatureVerifier.PLACEHOLDER_SECRET, "short-secret"})
    void misconfiguredSecretRejectsEveryWebhookWith503(String secret) {
        SandboxHmacPaymentGateway gateway = gateway(secret);
        byte[] body = payload("evt_1001", "txn_9001", ORDER_ID, "CAPTURED", "500000", "VND");
        String signedWithSameWeakSecret = secret == null || secret.isEmpty() ? "00".repeat(32) : sign(secret, body);

        assertStatus(() -> gateway.verifyAndParse(body, headers(signedWithSameWeakSecret, null)),
                HttpStatus.SERVICE_UNAVAILABLE, "PAYMENT_WEBHOOK_NOT_CONFIGURED");
        assertStatus(() -> gateway.verifyAndParse(body, headers(sign(SECRET, body), null)),
                HttpStatus.SERVICE_UNAVAILABLE, "PAYMENT_WEBHOOK_NOT_CONFIGURED");
    }

    @Test
    void misconfiguredSecretLogsExactlyOneStartupWarningWithoutTheSecret() {
        gateway(HmacSha256WebhookSignatureVerifier.PLACEHOLDER_SECRET);

        assertThat(logs.list).hasSize(1);
        assertThat(logs.list.get(0).getLevel()).isEqualTo(Level.WARN);
        assertThat(logs.list.get(0).getFormattedMessage())
                .contains("flowerchain.payment.webhook-secret")
                .doesNotContain(HmacSha256WebhookSignatureVerifier.PLACEHOLDER_SECRET);
    }

    @Test
    void usableSecretLogsNoStartupWarning() {
        gateway(SECRET);
        assertThat(logs.list).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "[]",
        "{}",
        "{\"eventId\":\"evt_1\",\"transactionId\":\"txn_1\",\"orderId\":\"7d2c9a4e-3f1b-4c8d-9e0f-112233445566\","
                + "\"status\":\"CAPTURED\",\"amount\":\"100\",\"currencyCode\":\"VND\"}",
        "{\"eventId\":\"evt\\n1\",\"transactionId\":\"txn_1\",\"orderId\":\"7d2c9a4e-3f1b-4c8d-9e0f-112233445566\","
                + "\"status\":\"CAPTURED\",\"amount\":\"100\",\"currencyCode\":\"VND\",\"occurredAt\":\"2026-11-25T07:59:00Z\"}",
        "{\"eventId\":\"evt_1\",\"transactionId\":\"txn_1\",\"orderId\":\"not-a-uuid\","
                + "\"status\":\"CAPTURED\",\"amount\":\"100\",\"currencyCode\":\"VND\",\"occurredAt\":\"2026-11-25T07:59:00Z\"}",
        "{\"eventId\":\"evt_1\",\"transactionId\":\"txn_1\",\"orderId\":\"7d2c9a4e-3f1b-4c8d-9e0f-112233445566\","
                + "\"status\":\"PAID\",\"amount\":\"100\",\"currencyCode\":\"VND\",\"occurredAt\":\"2026-11-25T07:59:00Z\"}",
        "{\"eventId\":\"evt_1\",\"transactionId\":\"txn_1\",\"orderId\":\"7d2c9a4e-3f1b-4c8d-9e0f-112233445566\","
                + "\"status\":\"CAPTURED\",\"amount\":\"-1\",\"currencyCode\":\"VND\",\"occurredAt\":\"2026-11-25T07:59:00Z\"}",
        "{\"eventId\":\"evt_1\",\"transactionId\":\"txn_1\",\"orderId\":\"7d2c9a4e-3f1b-4c8d-9e0f-112233445566\","
                + "\"status\":\"CAPTURED\",\"amount\":\"1.234\",\"currencyCode\":\"VND\",\"occurredAt\":\"2026-11-25T07:59:00Z\"}",
        "{\"eventId\":\"evt_1\",\"transactionId\":\"txn_1\",\"orderId\":\"7d2c9a4e-3f1b-4c8d-9e0f-112233445566\","
                + "\"status\":\"CAPTURED\",\"amount\":100,\"currencyCode\":\"VND\",\"occurredAt\":\"2026-11-25T07:59:00Z\"}",
        "{\"eventId\":\"evt_1\",\"transactionId\":\"txn_1\",\"orderId\":\"7d2c9a4e-3f1b-4c8d-9e0f-112233445566\","
                + "\"status\":\"CAPTURED\",\"amount\":\"100\",\"currencyCode\":\"vnd\",\"occurredAt\":\"2026-11-25T07:59:00Z\"}",
        "{\"eventId\":\"evt_1\",\"transactionId\":\"txn_1\",\"orderId\":\"7d2c9a4e-3f1b-4c8d-9e0f-112233445566\","
                + "\"status\":\"CAPTURED\",\"amount\":\"100\",\"currencyCode\":\"VND\",\"occurredAt\":\"yesterday\"}"
    })
    void authenticButMalformedPayloadIsRejectedWith400(String json) {
        SandboxHmacPaymentGateway gateway = gateway(SECRET);
        byte[] body = json.getBytes(StandardCharsets.UTF_8);

        assertStatus(() -> gateway.verifyAndParse(body, headers(sign(SECRET, body), null)),
                HttpStatus.BAD_REQUEST, "PAYMENT_WEBHOOK_PAYLOAD_INVALID");
    }

    private SandboxHmacPaymentGateway gateway(String secret) {
        return new SandboxHmacPaymentGateway(secret, CLOCK, objectMapper);
    }

    private static HttpHeaders headers(String signature, String timestamp) {
        HttpHeaders headers = new HttpHeaders();
        if (signature != null) {
            headers.set(SandboxHmacPaymentGateway.SIGNATURE_HEADER, signature);
        }
        if (timestamp != null) {
            headers.set(SandboxHmacPaymentGateway.TIMESTAMP_HEADER, timestamp);
        }
        return headers;
    }

    private static void assertStatus(Runnable call, HttpStatus status, String errorCode) {
        assertThatThrownBy(call::run)
                .isInstanceOfSatisfying(FlowerChainException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(status);
                    assertThat(e.getErrorCode()).isEqualTo(errorCode);
                });
    }
}
