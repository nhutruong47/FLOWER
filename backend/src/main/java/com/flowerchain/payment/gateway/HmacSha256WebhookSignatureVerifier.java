package com.flowerchain.payment.gateway;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * HMAC-SHA256 webhook signature verification over the raw body bytes (REQ-009).
 *
 * <p>Signing scheme {@value #SCHEME_VERSION} (ADR-0007 leaves the details open; security review F3):
 *
 * <ul>
 *   <li>The timestamp header is required: Unix epoch seconds, decimal digits only.
 *   <li>Signed input: {@code "v1:" + timestamp + "." + rawBody} (the body bytes exactly as received). The version
 *       prefix lets a future scheme change without any ambiguity between forms.
 *   <li>Signature header: hex of the 32-byte HMAC-SHA256 (exactly 64 hex characters, either case).
 *   <li>The timestamp must be within {@link #TIMESTAMP_TOLERANCE} of the server clock in either direction, so a
 *       captured request cannot be replayed later (duplicates inside the window are deduplicated by event ID).
 *   <li>Signatures are compared in constant time with {@link MessageDigest#isEqual(byte[], byte[])}.
 * </ul>
 *
 * <p>Fails closed: if the secret is blank, a placeholder, or shorter than {@link #MIN_SECRET_LENGTH} characters
 * (after stripping surrounding whitespace), every verification returns {@link Result#SECRET_NOT_CONFIGURED}. The
 * key is the stripped secret, the same value that was checked. The secret is never exposed by this class.
 */
public final class HmacSha256WebhookSignatureVerifier implements WebhookSignatureVerifier {

    public static final String SCHEME_VERSION = "v1";

    /** The development fallback in {@code application.yml}; never accepted as a real secret. */
    public static final String PLACEHOLDER_SECRET = "dev-only-placeholder-webhook-secret-change-in-env";

    public static final int MIN_SECRET_LENGTH = 32;

    public static final Duration TIMESTAMP_TOLERANCE = Duration.ofMinutes(5);

    /** Template values that must never be used as a secret, even when they are long enough. */
    private static final List<String> PLACEHOLDER_MARKERS =
            List.of("placeholder", "replace_with", "change_me", "changeme");

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final byte[] SCHEME_PREFIX = (SCHEME_VERSION + ":").getBytes(StandardCharsets.US_ASCII);
    private static final Pattern HEX_SIGNATURE = Pattern.compile("[0-9a-fA-F]{64}");
    private static final Pattern EPOCH_SECONDS = Pattern.compile("[0-9]{1,12}");

    private final SecretKeySpec key;
    private final Clock clock;

    public HmacSha256WebhookSignatureVerifier(String secret, Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.key = isUsableSecret(secret)
                ? new SecretKeySpec(secret.strip().getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM)
                : null;
    }

    /** {@code false} for a null, blank, placeholder-like or too-short secret (judged after stripping). */
    public static boolean isUsableSecret(String secret) {
        if (secret == null || secret.isBlank()) {
            return false;
        }
        String stripped = secret.strip();
        String lower = stripped.toLowerCase(Locale.ROOT);
        return !PLACEHOLDER_SECRET.equals(stripped)
                && PLACEHOLDER_MARKERS.stream().noneMatch(lower::contains)
                && stripped.length() >= MIN_SECRET_LENGTH;
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
        if (timestampHeader == null || timestampHeader.isBlank()) {
            return Result.MISSING_TIMESTAMP;
        }
        String timestamp = timestampHeader.strip();
        if (!EPOCH_SECONDS.matcher(timestamp).matches()) {
            return Result.MALFORMED_TIMESTAMP;
        }

        byte[] expected = hmac(timestamp, rawBody == null ? new byte[0] : rawBody);
        byte[] provided = HexFormat.of().parseHex(signatureHex);
        if (!MessageDigest.isEqual(expected, provided)) {
            return Result.SIGNATURE_MISMATCH;
        }

        long skewSeconds = Math.abs(Instant.now(clock).getEpochSecond() - Long.parseLong(timestamp));
        if (skewSeconds > TIMESTAMP_TOLERANCE.toSeconds()) {
            return Result.STALE_TIMESTAMP;
        }
        return Result.VALID;
    }

    private byte[] hmac(String timestamp, byte[] rawBody) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(key);
            mac.update(SCHEME_PREFIX);
            mac.update(timestamp.getBytes(StandardCharsets.US_ASCII));
            mac.update((byte) '.');
            return mac.doFinal(rawBody);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC-SHA256 is not available", e);
        }
    }
}
