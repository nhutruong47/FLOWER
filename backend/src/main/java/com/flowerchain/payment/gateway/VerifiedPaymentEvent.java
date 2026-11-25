package com.flowerchain.payment.gateway;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A payment provider event whose signature has been verified on the server (REQ-009). Only a
 * {@link PaymentGatewayPort} creates these, after verification succeeded.
 *
 * @param provider provider code, e.g. {@code sandbox-hmac}
 * @param eventId the provider's unique event ID (deduplication key, REQ-010)
 * @param transactionId the provider's transaction ID (second deduplication key, REQ-010)
 * @param orderId the FlowerChain order the event refers to
 * @param status what happened to the payment
 * @param amount the amount the provider reports, compared with the order total before anything is applied
 * @param currencyCode ISO 4217 currency code reported by the provider
 * @param occurredAt when the provider says the event happened (UTC)
 */
public record VerifiedPaymentEvent(
        String provider,
        String eventId,
        String transactionId,
        UUID orderId,
        Status status,
        BigDecimal amount,
        String currencyCode,
        Instant occurredAt) {

    /** Payment state reported by the provider. */
    public enum Status {
        CAPTURED,
        FAILED
    }
}
