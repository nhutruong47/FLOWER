package com.flowerchain.payment.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** A recorded refund (FR-027, BRULE-025). The money movement itself happens at the provider. */
@Entity
@Table(name = "payment_refunds")
public class PaymentRefundEntity {

    @Id
    private UUID id;

    @Column(name = "payment_id", nullable = false)
    private UUID paymentId;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "refund_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal refundAmount;

    @Column(name = "reason", nullable = false, length = 500)
    private String reason;

    @Column(name = "external_refund_ref", length = 128)
    private String externalRefundRef;

    @Column(name = "approved_by_staff_id", nullable = false)
    private UUID approvedByStaffId;

    @Column(name = "refunded_at", nullable = false)
    private Instant refundedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected PaymentRefundEntity() {}

    public PaymentRefundEntity(
            UUID id,
            UUID paymentId,
            UUID orderId,
            BigDecimal refundAmount,
            String reason,
            String externalRefundRef,
            UUID approvedByStaffId,
            Instant now) {
        this.id = id;
        this.paymentId = paymentId;
        this.orderId = orderId;
        this.refundAmount = refundAmount;
        this.reason = reason;
        this.externalRefundRef = externalRefundRef;
        this.approvedByStaffId = approvedByStaffId;
        this.refundedAt = now;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public UUID getPaymentId() {
        return paymentId;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public BigDecimal getRefundAmount() {
        return refundAmount;
    }

    public String getReason() {
        return reason;
    }

    public String getExternalRefundRef() {
        return externalRefundRef;
    }

    public UUID getApprovedByStaffId() {
        return approvedByStaffId;
    }

    public Instant getRefundedAt() {
        return refundedAt;
    }
}
