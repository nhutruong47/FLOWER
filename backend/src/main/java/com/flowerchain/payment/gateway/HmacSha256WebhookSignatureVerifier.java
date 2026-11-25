package com.flowerchain.payment.gateway;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Objects;
import java.util.regex.Pattern;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * HMAC-SHA256 webhook signature verification over the raw body bytes (REQ-009).
 *
 * <p>Signing scheme (ADR-0007 leaves the details open, so this is the documented default):
 *
 * <ul>
 *   <li>Signature header: lowercase or uppercase hex of the 32-byte HMAC-SHA256 (exactly 64 hex characters).
 *   <li>Without a timestamp header, the signed input is the raw body bytes.
 *   <li>With a timestamp header (Unix epoch seconds, decimal digits only), the signed input is
 *       {@code timestamp + "." + rawBody}, and the timestamp must be within {@link #TIMESTAMP_TOLERANCE} of
 *       the server clock in either direction.
 *   <li>Signatures are compared in constant time with {@link MessageDigest#isEqual(byte[], byte[])}.
 * </ul>
 *
 * <p>Fails closed: if the secret is blank, the development placeholder from {@code application.yml}, or
 * shorter than {@link #MIN_SECRET_LENGTH} characters, every verification returns
 * {@link Result#SECRET_NOT_CONFIGURED}. The secret is never exposed by this class.
 */
public final class HmacSha256WebhookSignatureVerifier implements WebhookSignatureVerifier {

    /** The development fallback in {@code application.yml}; never accepted as a real secret. */
    public static final String PLACEHOLDER_SECRET = "dev-only-placeholder-webhook-secret-change-in-env";

    public static final int MIN_SECRET_LENGTH = 32;

    public static final Duration TIMESTAMP_TOLERANCE = Duration.ofMinutes(5);

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final Pattern HEX_SIGNATURE = Pattern.compile("[0-9a-fA-F]{64}");
    private static final Pattern EPOCH_SECONDS = Pattern.compile("[0-9]{1,12}");

    private final SecretKeySpec key;
    private final Clock clock;

    public HmacSha256WebhookSignatureVerifier(String secret, Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.key = isUsableSecret(secret)
                ? new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM)
                : null;
    }

    /** {@code false} for a null, blank, placeholder or too-short secret. */
    public static boolean isUsableSecret(String secret) {
        if (secret == null || secret.isBlank()) {
            return false;
        }
        String stripped = secret.strip();
        return !PLACEHOLDER_SECRET.equals(stripped) && stripped.length() >= MIN_SECRET_LENGTH;
    }

    @Override
    public boolean isConfigured() {
        return key != null;
    }

    @Override
    public Result verify(byte[] rawBody, String signatureHeader, String timestampHeader) {
        if (key == null) {
            return Result.SECRET_NOT_CONFIGURED;
        }
        if (signatureHeader == null || signatureHeader.isBlank()) {
            return Result.MISSING_SIGNATURE;
        }
        String signatureHex = signatureHeader.strip();
        if (!HEX_SIGNATURE.matcher(signatureHex).matches()) {
            return Result.MALFORMED_SIGNATURE;
        }
        String timestamp = null;
        if (timestampHeader != null) {
            timestamp = timestampHeader.strip();
            if (!EPOCH_SECONDS.matcher(timestamp).matches()) {
                return Result.MALFORMED_TIMESTAMP;
            }
        }

        byte[] expected = hmac(timestamp, rawBody == null ? new byte[0] : rawBody);
        byte[] provided = HexFormat.of().parseHex(signatureHex);
        if (!MessageDigest.isEqual(expected, provided)) {
            return Result.SIGNATURE_MISMATCH;
        }

        if (timestamp != null) {
            long skewSeconds = Math.abs(Instant.now(clock).getEpochSecond() - Long.parseLong(timestamp));
            if (skewSeconds > TIMESTAMP_TOLERANCE.toSeconds()) {
                return Result.STALE_TIMESTAMP;
            }
        }
        return Result.VALID;
    }

    private byte[] hmac(String timestamp, byte[] rawBody) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(key);
            if (timestamp != null) {
                mac.update(timestamp.getBytes(StandardCharsets.US_ASCII));
                mac.update((byte) '.');
            }
            return mac.doFinal(rawBody);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC-SHA256 is not available", e);
        }
    }
}
