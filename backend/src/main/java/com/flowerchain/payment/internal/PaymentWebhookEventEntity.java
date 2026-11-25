package com.flowerchain.payment.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * One verified provider event. {@code UNIQUE (provider, provider_event_id)} makes each event count once
 * (REQ-010). Only verified events are stored ({@code CHECK (signature_verified = true)}).
 */
@Entity
@Table(name = "payment_webhook_events")
public class PaymentWebhookEventEntity {

    @Id
    private UUID id;

    @Column(name = "provider", nullable = false, length = 32)
    private String provider;

    @Column(name = "provider_event_id", nullable = false, length = 128)
    private String providerEventId;

    @Column(name = "provider_transaction_id", length = 128)
    private String providerTransactionId;

    @Column(name = "order_id")
    private UUID orderId;

    @Column(name = "signature_verified", nullable = false)
    private boolean signatureVerified;

    @Column(name = "processing_outcome", nullable = false, length = 32)
    private String processingOutcome;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected PaymentWebhookEventEntity() {}

    /** Records a verified event; {@code signature_verified} is always {@code true}. */
    public PaymentWebhookEventEntity(
            UUID id,
            String provider,
            String providerEventId,
            String providerTransactionId,
            UUID orderId,
            String processingOutcome,
            Instant now) {
        this.id = id;
        this.provider = provider;
        this.providerEventId = providerEventId;
        this.providerTransactionId = providerTransactionId;
        this.orderId = orderId;
        this.signatureVerified = true;
        this.processingOutcome = processingOutcome;
        this.receivedAt = now;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public String getProvider() {
        return provider;
    }

    public String getProviderEventId() {
        return providerEventId;
    }

    public String getProviderTransactionId() {
        return providerTransactionId;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public boolean isSignatureVerified() {
        return signatureVerified;
    }

    public String getProcessingOutcome() {
        return processingOutcome;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }
}
