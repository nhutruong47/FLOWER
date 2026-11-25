package com.flowerchain.payment.application;

/**
 * What happened to a verified payment webhook event. Stored in
 * {@code payment_webhook_events.processing_outcome} (max 32 characters) and returned in
 * {@code WebhookAckResponse.outcome}. Every outcome is acknowledged with HTTP 200 so the provider stops
 * retrying; only {@link #PROCESSED} changes payment or order state.
 */
public enum WebhookOutcome {

    /** First delivery: the payment was recorded (CAPTURED marks the order paid; FAILED leaves it unpaid). */
    PROCESSED,

    /** The event ID, or the same result for the same transaction, was already processed. No side effects. */
    ALREADY_PROCESSED_IDEMPOTENT,

    /**
     * Out-of-order or stale event, e.g. a capture for a cancelled or already paid order, or FAILED after
     * CAPTURED. No status change; audited (a stale capture is flagged for manual reconciliation).
     */
    IGNORED_STALE,

    /** Amount or currency differs from the order's total. The order is never marked paid; audited. */
    REJECTED_AMOUNT_MISMATCH,

    /** The transaction ID is already recorded for a different order. No side effects; audited. */
    REJECTED_ORDER_MISMATCH,

    /** The order ID does not exist. Recorded and audited, nothing else changes. */
    REJECTED_UNKNOWN_ORDER
}
