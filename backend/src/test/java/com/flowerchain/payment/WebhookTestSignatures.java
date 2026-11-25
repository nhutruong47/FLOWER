package com.flowerchain.payment;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Test helper that signs webhook bodies the way a provider would, independently of the production verifier.
 * Scheme: hex(HMAC-SHA256(secret, rawBody)), or hex(HMAC-SHA256(secret, timestamp + "." + rawBody)).
 */
public final class WebhookTestSignatures {

    /** A usable test secret (at least 32 characters, not the placeholder). Never used outside tests. */
    public static final String SECRET = "unit-and-it-test-webhook-secret-0123456789";

    /** A different usable secret, for "signed with the wrong key" cases. */
    public static final String OTHER_SECRET = "attacker-chosen-webhook-secret-9876543210";

    private WebhookTestSignatures() {}

    public static String sign(String secret, byte[] rawBody) {
        return hmacHex(secret, rawBody);
    }

    public static String sign(String secret, String timestamp, byte[] rawBody) {
        ByteArrayOutputStream signedInput = new ByteArrayOutputStream();
        signedInput.writeBytes(timestamp.getBytes(StandardCharsets.US_ASCII));
        signedInput.write('.');
        signedInput.writeBytes(rawBody);
        return hmacHex(secret, signedInput.toByteArray());
    }

    public static byte[] payload(
            String eventId, String transactionId, UUID orderId, String status, String amount, String currencyCode) {
        String json = "{\"eventId\":\"" + eventId + "\","
                + "\"transactionId\":\"" + transactionId + "\","
                + "\"orderId\":\"" + orderId + "\","
                + "\"status\":\"" + status + "\","
                + "\"amount\":\"" + amount + "\","
                + "\"currencyCode\":\"" + currencyCode + "\","
                + "\"occurredAt\":\"2026-11-25T07:59:00Z\"}";
        return json.getBytes(StandardCharsets.UTF_8);
    }

    private static String hmacHex(String secret, byte[] signedInput) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(signedInput));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
