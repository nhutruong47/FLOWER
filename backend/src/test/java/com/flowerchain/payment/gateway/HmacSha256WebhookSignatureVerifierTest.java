package com.flowerchain.payment.gateway;

import static com.flowerchain.payment.WebhookTestSignatures.OTHER_SECRET;
import static com.flowerchain.payment.WebhookTestSignatures.SECRET;
import static com.flowerchain.payment.WebhookTestSignatures.sign;
import static org.assertj.core.api.Assertions.assertThat;

import com.flowerchain.payment.gateway.WebhookSignatureVerifier.Result;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

/** TEST-011 (unit level): signature verification on raw bytes, REQ-009. */
class HmacSha256WebhookSignatureVerifierTest {

    private static final Instant NOW = Instant.parse("2026-11-25T08:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final byte[] BODY =
            "{\"eventId\":\"evt_1001\",\"amount\":\"500000\"}".getBytes(StandardCharsets.UTF_8);
    private static final String NOW_SECONDS = Long.toString(NOW.getEpochSecond());

    private final HmacSha256WebhookSignatureVerifier verifier = new HmacSha256WebhookSignatureVerifier(SECRET, CLOCK);

    @Test
    void validSignatureOverRawBodyIsAccepted() {
        assertThat(verifier.isConfigured()).isTrue();
        assertThat(verifier.verify(BODY, sign(SECRET, BODY), null)).isEqualTo(Result.VALID);
    }

    @Test
    void validSignatureWithFreshTimestampIsAccepted() {
        assertThat(verifier.verify(BODY, sign(SECRET, NOW_SECONDS, BODY), NOW_SECONDS)).isEqualTo(Result.VALID);
    }

    @Test
    void uppercaseHexAndSurroundingWhitespaceAreAccepted() {
        String signature = " " + sign(SECRET, BODY).toUpperCase(Locale.ROOT) + " ";
        assertThat(verifier.verify(BODY, signature, null)).isEqualTo(Result.VALID);
    }

    @Test
    void timestampExactlyAtTheFiveMinuteBoundaryIsAccepted() {
        String fiveMinutesAgo = Long.toString(NOW.getEpochSecond() - 300);
        String fiveMinutesAhead = Long.toString(NOW.getEpochSecond() + 300);
        assertThat(verifier.verify(BODY, sign(SECRET, fiveMinutesAgo, BODY), fiveMinutesAgo)).isEqualTo(Result.VALID);
        assertThat(verifier.verify(BODY, sign(SECRET, fiveMinutesAhead, BODY), fiveMinutesAhead))
                .isEqualTo(Result.VALID);
    }

    @Test
    void forgedSignatureIsRejected() {
        String forged = "0f".repeat(32);
        assertThat(verifier.verify(BODY, forged, null)).isEqualTo(Result.SIGNATURE_MISMATCH);
    }

    @Test
    void signatureMadeWithAnotherSecretIsRejected() {
        assertThat(verifier.verify(BODY, sign(OTHER_SECRET, BODY), null)).isEqualTo(Result.SIGNATURE_MISMATCH);
        assertThat(verifier.verify(BODY, sign(OTHER_SECRET, NOW_SECONDS, BODY), NOW_SECONDS))
                .isEqualTo(Result.SIGNATURE_MISMATCH);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   "})
    void missingSignatureHeaderIsRejected(String signatureHeader) {
        assertThat(verifier.verify(BODY, signatureHeader, null)).isEqualTo(Result.MISSING_SIGNATURE);
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "sha256=0f0f", "zz0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f"})
    void malformedSignatureIsRejected(String signatureHeader) {
        assertThat(verifier.verify(BODY, signatureHeader, null)).isEqualTo(Result.MALFORMED_SIGNATURE);
    }

    @Test
    void signatureOfWrongLengthIsRejected() {
        String tooShort = sign(SECRET, BODY).substring(2);
        String withPrefix = "sha256=" + sign(SECRET, BODY);
        assertThat(verifier.verify(BODY, tooShort, null)).isEqualTo(Result.MALFORMED_SIGNATURE);
        assertThat(verifier.verify(BODY, withPrefix, null)).isEqualTo(Result.MALFORMED_SIGNATURE);
    }

    @Test
    void tamperedBodyIsRejected() {
        String signature = sign(SECRET, BODY);
        byte[] tampered = "{\"eventId\":\"evt_1001\",\"amount\":\"1\"}".getBytes(StandardCharsets.UTF_8);
        byte[] withTrailingSpace = (new String(BODY, StandardCharsets.UTF_8) + " ").getBytes(StandardCharsets.UTF_8);
        assertThat(verifier.verify(tampered, signature, null)).isEqualTo(Result.SIGNATURE_MISMATCH);
        assertThat(verifier.verify(withTrailingSpace, signature, null)).isEqualTo(Result.SIGNATURE_MISMATCH);
    }

    @Test
    void staleTimestampIsRejectedEvenWithAuthenticSignature() {
        String sixMinutesAgo = Long.toString(NOW.getEpochSecond() - 360);
        String justOutside = Long.toString(NOW.getEpochSecond() - 301);
        assertThat(verifier.verify(BODY, sign(SECRET, sixMinutesAgo, BODY), sixMinutesAgo))
                .isEqualTo(Result.STALE_TIMESTAMP);
        assertThat(verifier.verify(BODY, sign(SECRET, justOutside, BODY), justOutside))
                .isEqualTo(Result.STALE_TIMESTAMP);
    }

    @Test
    void timestampTooFarInTheFutureIsRejected() {
        String sixMinutesAhead = Long.toString(NOW.getEpochSecond() + 360);
        assertThat(verifier.verify(BODY, sign(SECRET, sixMinutesAhead, BODY), sixMinutesAhead))
                .isEqualTo(Result.STALE_TIMESTAMP);
    }

    @Test
    void timestampCannotBeAddedRemovedOrChangedAfterSigning() {
        String signedWithTimestamp = sign(SECRET, NOW_SECONDS, BODY);
        String signedWithoutTimestamp = sign(SECRET, BODY);
        String otherFreshTimestamp = Long.toString(NOW.getEpochSecond() - 10);

        assertThat(verifier.verify(BODY, signedWithTimestamp, null)).isEqualTo(Result.SIGNATURE_MISMATCH);
        assertThat(verifier.verify(BODY, signedWithoutTimestamp, NOW_SECONDS)).isEqualTo(Result.SIGNATURE_MISMATCH);
        assertThat(verifier.verify(BODY, signedWithTimestamp, otherFreshTimestamp))
                .isEqualTo(Result.SIGNATURE_MISMATCH);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "abc", "-1764057600", "1764057600.5", "2026-11-25T08:00:00Z", "1234567890123"})
    void malformedTimestampIsRejected(String timestampHeader) {
        assertThat(verifier.verify(BODY, sign(SECRET, BODY), timestampHeader)).isEqualTo(Result.MALFORMED_TIMESTAMP);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {
        "",
        "    ",
        HmacSha256WebhookSignatureVerifier.PLACEHOLDER_SECRET,
        "  " + HmacSha256WebhookSignatureVerifier.PLACEHOLDER_SECRET + "  ",
        "only-31-characters-long-secret!"
    })
    void unusableSecretFailsClosedForEveryWebhook(String secret) {
        HmacSha256WebhookSignatureVerifier misconfigured = new HmacSha256WebhookSignatureVerifier(secret, CLOCK);
        // A signature computed with that same weak secret must not get through either.
        String signature = secret == null || secret.isEmpty() ? "00".repeat(32) : sign(secret, BODY);

        assertThat(HmacSha256WebhookSignatureVerifier.isUsableSecret(secret)).isFalse();
        assertThat(misconfigured.isConfigured()).isFalse();
        assertThat(misconfigured.verify(BODY, signature, null)).isEqualTo(Result.SECRET_NOT_CONFIGURED);
        assertThat(misconfigured.verify(BODY, null, null)).isEqualTo(Result.SECRET_NOT_CONFIGURED);
    }

    @Test
    void secretOfExactly32CharactersIsUsable() {
        String secret = "a".repeat(32);
        assertThat(HmacSha256WebhookSignatureVerifier.isUsableSecret(secret)).isTrue();
        HmacSha256WebhookSignatureVerifier configured = new HmacSha256WebhookSignatureVerifier(secret, CLOCK);
        assertThat(configured.verify(BODY, sign(secret, BODY), null)).isEqualTo(Result.VALID);
    }
}
