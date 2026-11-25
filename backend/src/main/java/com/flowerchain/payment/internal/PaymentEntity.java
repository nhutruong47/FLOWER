package com.flowerchain.payment.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payments")
public class PaymentEntity {

    public static final String STATUS_INITIATED = "INITIATED";
    public static final String STATUS_CAPTURED = "CAPTURED";
    public static final String STATUS_FAILED = "FAILED";
    public static final String STATUS_REFUNDED = "REFUNDED";

    @Id
    private UUID id;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "provider", nullable = false, length = 32)
    private String provider;

    @Column(name = "payment_method", nullable = false, length = 32)
    private String paymentMethod;

    @Column(name = "provider_transaction_id", length = 128)
    private String providerTransactionId;

    @Column(name = "status", nullable = false, length = 24)
    private String status;

    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode;

    @Column(name = "captured_at")
    private Instant capturedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected PaymentEntity() {}

    public PaymentEntity(
            UUID id,
            UUID orderId,
            String provider,
            String paymentMethod,
            String providerTransactionId,
            String status,
            BigDecimal amount,
            String currencyCode,
            Instant capturedAt,
            Instant now) {
        this.id = id;
        this.orderId = orderId;
        this.provider = provider;
        this.paymentMethod = paymentMethod;
        this.providerTransactionId = providerTransactionId;
        this.status = status;
        this.amount = amount;
        this.currencyCode = currencyCode;
        this.capturedAt = capturedAt;
        this.createdAt = now;
        this.updatedAt = now;
    }

    /** A verified capture for a transaction previously reported as initiated or failed. */
    public void markCaptured(BigDecimal capturedAmount, String capturedCurrencyCode, Instant capturedAt, Instant now) {
        this.status = STATUS_CAPTURED;
        this.amount = capturedAmount;
        this.currencyCode = capturedCurrencyCode;
        this.capturedAt = capturedAt;
        this.updatedAt = now;
    }

    public void markFailed(Instant now) {
        this.status = STATUS_FAILED;
        this.updatedAt = now;
    }

    public void markRefunded(Instant now) {
        this.status = STATUS_REFUNDED;
        this.updatedAt = now;
    }

    /** {@code true} if money was captured for this payment (it may since have been refunded). */
    public boolean isCapturedOrRefunded() {
        return STATUS_CAPTURED.equals(status) || STATUS_REFUNDED.equals(status);
    }

    public UUID getId() {
        return id;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public String getProvider() {
        return provider;
    }

    public String getPaymentMethod() {
        return paymentMethod;
    }

    public String getProviderTransactionId() {
        return providerTransactionId;
    }

    public String getStatus() {
        return status;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrencyCode() {
        return currencyCode;
    }

    public Instant getCapturedAt() {
        return capturedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
