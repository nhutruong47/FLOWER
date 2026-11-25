package com.flowerchain.payment;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Test helper that signs webhook bodies the way a provider would, independently of the production verifier.
 * Scheme v1: hex(HMAC-SHA256(secret, "v1:" + timestamp + "." + rawBody)).
 */
public final class WebhookTestSignatures {

    /** A usable test secret (at least 32 characters, no placeholder marker). Never used outside tests. */
    public static final String SECRET = "unit-and-it-test-webhook-secret-0123456789";

    /** A different usable secret, for "signed with the wrong key" cases. */
    public static final String OTHER_SECRET = "attacker-chosen-webhook-secret-9876543210";

    private WebhookTestSignatures() {}

    /** The v1 signature: HMAC over {@code "v1:" + timestamp + "." + rawBody}. */
    public static String sign(String secret, String timestamp, byte[] rawBody) {
        return hmacHex(secret, concat(("v1:" + timestamp + ".").getBytes(StandardCharsets.US_ASCII), rawBody));
    }

    /** The pre-review form without the version prefix, which must now be rejected. */
    public static String signWithoutVersion(String secret, String timestamp, byte[] rawBody) {
        return hmacHex(secret, concat((timestamp + ".").getBytes(StandardCharsets.US_ASCII), rawBody));
    }

    /** The pre-review body-only form, which must now be rejected. */
    public static String signBodyOnly(String secret, byte[] rawBody) {
        return hmacHex(secret, rawBody);
    }

    /** Signature and timestamp headers for a v1-signed request. */
    public static Map<String, String> signedHeaders(String secret, long epochSeconds, byte[] rawBody) {
        String timestamp = Long.toString(epochSeconds);
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("X-Payment-Signature", sign(secret, timestamp, rawBody));
        headers.put("X-Payment-Timestamp", timestamp);
        return headers;
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

    private static byte[] concat(byte[] prefix, byte[] body) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(prefix);
        out.writeBytes(body);
        return out.toByteArray();
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
