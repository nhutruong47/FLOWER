package com.flowerchain.payment.gateway;

import org.springframework.http.HttpHeaders;

/**
 * Provider-neutral port for server-to-server payment webhooks (ADR-0007 / DEC-022, REQ-009).
 *
 * <p>Each payment provider plugs in as one adapter. Until the provider question (B4) is answered, only the
 * {@code sandbox-hmac} adapter exists; requests for any other provider are rejected before their body is
 * looked at.
 */
public interface PaymentGatewayPort {

    /** The {@code {provider}} path segment of {@code POST /api/v1/webhooks/payments/{provider}} served here. */
    String providerCode();

    /**
     * Verifies the authenticity of the raw request bytes with the provider's signing scheme and only then
     * parses them. Implementations must not parse, persist or log the body before the signature is verified,
     * and must never log the secret or the signature.
     *
     * @param rawBody the request body exactly as received
     * @param headers the request headers
     * @return the verified, parsed event
     * @throws com.flowerchain.shared.api.FlowerChainException 503 when the adapter has no usable secret (fails
     *     closed), 401 when the signature is missing or invalid, 400 when an authentic payload is malformed
     */
    VerifiedPaymentEvent verifyAndParse(byte[] rawBody, HttpHeaders headers);
}
