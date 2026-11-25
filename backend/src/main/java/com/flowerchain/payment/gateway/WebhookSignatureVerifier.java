package com.flowerchain.payment.gateway;

/** Verifies the signature of a raw webhook body. Implementations never throw for a bad signature. */
public interface WebhookSignatureVerifier {

    /** Outcome of a verification. Only {@link #VALID} lets a webhook through. */
    enum Result {
        VALID,
        /** No usable secret is configured, so every webhook is rejected (fail closed). */
        SECRET_NOT_CONFIGURED,
        MISSING_SIGNATURE,
        MALFORMED_SIGNATURE,
        MALFORMED_TIMESTAMP,
        SIGNATURE_MISMATCH,
        /** The signature is authentic but the signed timestamp is outside the allowed window (replay). */
        STALE_TIMESTAMP
    }

    /** Whether a usable secret is configured. When {@code false}, {@link #verify} always fails. */
    boolean isConfigured();

    /**
     * @param rawBody the request body exactly as received
     * @param signatureHeader the signature header value, or {@code null} if absent
     * @param timestampHeader the timestamp header value, or {@code null} if absent
     */
    Result verify(byte[] rawBody, String signatureHeader, String timestampHeader);
}
