package com.flowerchain.payment.gateway;

import static com.flowerchain.payment.WebhookTestSignatures.OTHER_SECRET;
import static com.flowerchain.payment.WebhookTestSignatures.SECRET;
import static com.flowerchain.payment.WebhookTestSignatures.sign;
import static com.flowerchain.payment.WebhookTestSignatures.signBodyOnly;
import static com.flowerchain.payment.WebhookTestSignatures.signWithoutVersion;
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

/** TEST-011 (unit level): v1 signature verification on raw bytes, REQ-009. */
class HmacSha256WebhookSignatureVerifierTest {

    private static final Instant NOW = Instant.parse("2026-11-25T08:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final byte[] BODY =
            "{\"eventId\":\"evt_1001\",\"amount\":\"500000\"}".getBytes(StandardCharsets.UTF_8);
    private static final String TS = Long.toString(NOW.getEpochSecond());

    private final HmacSha256WebhookSignatureVerifier verifier = new HmacSha256WebhookSignatureVerifier(SECRET, CLOCK);

    @Test
    void validV1SignatureIsAccepted() {
        assertThat(verifier.isConfigured()).isTrue();
        assertThat(verifier.verify(BODY, sign(SECRET, TS, BODY), TS)).isEqualTo(Result.VALID);
    }

    @Test
    void uppercaseHexAndSurroundingWhitespaceAreAccepted() {
        String signature = " " + sign(SECRET, TS, BODY).toUpperCase(Locale.ROOT) + " ";
        assertThat(verifier.verify(BODY, signature, " " + TS + " ")).isEqualTo(Result.VALID);
    }

    @Test
    void timestampExactlyAtTheFiveMinuteBoundaryIsAccepted() {
        String fiveMinutesAgo = Long.toString(NOW.getEpochSecond() - 300);
        String fiveMinutesAhead = Long.toString(NOW.getEpochSecond() + 300);
        assertThat(verifier.verify(BODY, sign(SECRET, fiveMinutesAgo, BODY), fiveMinutesAgo)).isEqualTo(Result.VALID);
        assertThat(verifier.verify(BODY, sign(SECRET, fiveMinutesAhead, BODY), fiveMinutesAhead))
                .isEqualTo(Result.VALID);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   "})
    void missingTimestampIsRejectedEvenWithABodyOnlySignature(String timestampHeader) {
        assertThat(verifier.verify(BODY, signBodyOnly(SECRET, BODY), timestampHeader))
                .isEqualTo(Result.MISSING_TIMESTAMP);
        assertThat(verifier.verify(BODY, sign(SECRET, TS, BODY), timestampHeader)).isEqualTo(Result.MISSING_TIMESTAMP);
    }

    @Test
    void preReviewSigningFormsAreRejected() {
        assertThat(verifier.verify(BODY, signWithoutVersion(SECRET, TS, BODY), TS)).isEqualTo(Result.SIGNATURE_MISMATCH);
        assertThat(verifier.verify(BODY, signBodyOnly(SECRET, BODY), TS)).isEqualTo(Result.SIGNATURE_MISMATCH);
    }

    @Test
    void forgedSignatureIsRejected() {
        assertThat(verifier.verify(BODY, "0f".repeat(32), TS)).isEqualTo(Result.SIGNATURE_MISMATCH);
    }

    @Test
    void signatureMadeWithAnotherSecretIsRejected() {
        assertThat(verifier.verify(BODY, sign(OTHER_SECRET, TS, BODY), TS)).isEqualTo(Result.SIGNATURE_MISMATCH);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   "})
    void missingSignatureHeaderIsRejected(String signatureHeader) {
        assertThat(verifier.verify(BODY, signatureHeader, TS)).isEqualTo(Result.MISSING_SIGNATURE);
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "sha256=0f0f", "zz0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f"})
    void malformedSignatureIsRejected(String signatureHeader) {
        assertThat(verifier.verify(BODY, signatureHeader, TS)).isEqualTo(Result.MALFORMED_SIGNATURE);
    }

    @Test
    void signatureOfWrongLengthOrWithPrefixIsRejected() {
        assertThat(verifier.verify(BODY, sign(SECRET, TS, BODY).substring(2), TS)).isEqualTo(Result.MALFORMED_SIGNATURE);
        assertThat(verifier.verify(BODY, "sha256=" + sign(SECRET, TS, BODY), TS)).isEqualTo(Result.MALFORMED_SIGNATURE);
        assertThat(verifier.verify(BODY, "v1=" + sign(SECRET, TS, BODY), TS)).isEqualTo(Result.MALFORMED_SIGNATURE);
    }

    @Test
    void tamperedBodyIsRejected() {
        String signature = sign(SECRET, TS, BODY);
        byte[] tampered = "{\"eventId\":\"evt_1001\",\"amount\":\"1\"}".getBytes(StandardCharsets.UTF_8);
        byte[] withTrailingSpace = (new String(BODY, StandardCharsets.UTF_8) + " ").getBytes(StandardCharsets.UTF_8);
        assertThat(verifier.verify(tampered, signature, TS)).isEqualTo(Result.SIGNATURE_MISMATCH);
        assertThat(verifier.verify(withTrailingSpace, signature, TS)).isEqualTo(Result.SIGNATURE_MISMATCH);
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
    void timestampCannotBeChangedAfterSigning() {
        String otherFreshTimestamp = Long.toString(NOW.getEpochSecond() - 10);
        assertThat(verifier.verify(BODY, sign(SECRET, TS, BODY), otherFreshTimestamp))
                .isEqualTo(Result.SIGNATURE_MISMATCH);
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "-1764057600", "1764057600.5", "2026-11-25T08:00:00Z", "1234567890123"})
    void malformedTimestampIsRejected(String timestampHeader) {
        assertThat(verifier.verify(BODY, sign(SECRET, TS, BODY), timestampHeader)).isEqualTo(Result.MALFORMED_TIMESTAMP);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {
        "",
        "    ",
        HmacSha256WebhookSignatureVerifier.PLACEHOLDER_SECRET,
        "  " + HmacSha256WebhookSignatureVerifier.PLACEHOLDER_SECRET + "  ",
        "only-31-characters-long-secret!",
        "replace_with_32_byte_hmac_secret",
        "replace_with_at_least_32_random_characters",
        "Change_Me_To_A_Long_Random_Secret_Value_0123",
        "my-production-PLACEHOLDER-secret-0123456789"
    })
    void unusableSecretFailsClosedForEveryWebhook(String secret) {
        HmacSha256WebhookSignatureVerifier misconfigured = new HmacSha256WebhookSignatureVerifier(secret, CLOCK);
        // A signature computed with that same weak secret must not get through either.
        String signature = secret == null || secret.isEmpty() ? "00".repeat(32) : sign(secret, TS, BODY);

        assertThat(HmacSha256WebhookSignatureVerifier.isUsableSecret(secret)).isFalse();
        assertThat(misconfigured.isConfigured()).isFalse();
        assertThat(misconfigured.verify(BODY, signature, TS)).isEqualTo(Result.SECRET_NOT_CONFIGURED);
        assertThat(misconfigured.verify(BODY, null, null)).isEqualTo(Result.SECRET_NOT_CONFIGURED);
    }

    @Test
    void secretOfExactly32CharactersIsUsable() {
        String secret = "a".repeat(32);
        assertThat(HmacSha256WebhookSignatureVerifier.isUsableSecret(secret)).isTrue();
        HmacSha256WebhookSignatureVerifier configured = new HmacSha256WebhookSignatureVerifier(secret, CLOCK);
        assertThat(configured.verify(BODY, sign(secret, TS, BODY), TS)).isEqualTo(Result.VALID);
    }

    @Test
    void keyIsTheStrippedSecretThatWasChecked() {
        // F7: usability is judged on the stripped value, so the key must be the stripped value too.
        HmacSha256WebhookSignatureVerifier padded = new HmacSha256WebhookSignatureVerifier("  " + SECRET + "\n", CLOCK);

        assertThat(padded.verify(BODY, sign(SECRET, TS, BODY), TS)).isEqualTo(Result.VALID);
        assertThat(padded.verify(BODY, sign("  " + SECRET + "\n", TS, BODY), TS)).isEqualTo(Result.SIGNATURE_MISMATCH);
    }

    @Test
    void paddingCannotMakeAShortSecretUsable() {
        assertThat(HmacSha256WebhookSignatureVerifier.isUsableSecret("short-secret" + " ".repeat(40))).isFalse();
    }
}
