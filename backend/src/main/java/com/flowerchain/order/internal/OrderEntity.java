package com.flowerchain.order.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "orders")
public class OrderEntity {

    @Id
    private UUID id;

    @Column(name = "order_number", nullable = false, unique = true, length = 64)
    private String orderNumber;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "processing_branch_id", nullable = false)
    private UUID processingBranchId;

    @Column(name = "fulfilment_type", nullable = false, length = 16)
    private String fulfilmentType;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "payment_status", nullable = false, length = 24)
    private String paymentStatus;

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode;

    @Column(name = "items_subtotal", nullable = false, precision = 19, scale = 2)
    private BigDecimal itemsSubtotal;

    @Column(name = "delivery_fee", nullable = false, precision = 19, scale = 2)
    private BigDecimal deliveryFee;

    @Column(name = "discount_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal discountAmount;

    @Column(name = "total_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "recipient_name", nullable = false, length = 150)
    private String recipientName;

    @Column(name = "recipient_phone", nullable = false, length = 32)
    private String recipientPhone;

    @Column(name = "delivery_street_address", length = 255)
    private String deliveryStreetAddress;

    @Column(name = "delivery_district", length = 100)
    private String deliveryDistrict;

    @Column(name = "delivery_city", length = 100)
    private String deliveryCity;

    @Column(name = "delivery_latitude", precision = 9, scale = 6)
    private BigDecimal deliveryLatitude;

    @Column(name = "delivery_longitude", precision = 9, scale = 6)
    private BigDecimal deliveryLongitude;

    @Column(name = "requested_slot_start")
    private Instant requestedSlotStart;

    @Column(name = "requested_slot_end")
    private Instant requestedSlotEnd;

    @Column(name = "reservation_expires_at")
    private Instant reservationExpiresAt;

    @Column(name = "cancellation_reason", length = 500)
    private String cancellationReason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected OrderEntity() {}

    public OrderEntity(
            UUID id,
            String orderNumber,
            UUID customerId,
            UUID processingBranchId,
            String fulfilmentType,
            String status,
            String paymentStatus,
            String currencyCode,
            BigDecimal itemsSubtotal,
            BigDecimal deliveryFee,
            BigDecimal discountAmount,
            BigDecimal totalAmount,
            String recipientName,
            String recipientPhone,
            String deliveryStreetAddress,
            String deliveryDistrict,
            String deliveryCity,
            BigDecimal deliveryLatitude,
            BigDecimal deliveryLongitude,
            Instant requestedSlotStart,
            Instant requestedSlotEnd,
            Instant reservationExpiresAt,
            Instant now) {
        this.id = id;
        this.orderNumber = orderNumber;
        this.customerId = customerId;
        this.processingBranchId = processingBranchId;
        this.fulfilmentType = fulfilmentType;
        this.status = status;
        this.paymentStatus = paymentStatus;
        this.currencyCode = currencyCode;
        this.itemsSubtotal = itemsSubtotal;
        this.deliveryFee = deliveryFee;
        this.discountAmount = discountAmount;
        this.totalAmount = totalAmount;
        this.recipientName = recipientName;
        this.recipientPhone = recipientPhone;
        this.deliveryStreetAddress = deliveryStreetAddress;
        this.deliveryDistrict = deliveryDistrict;
        this.deliveryCity = deliveryCity;
        this.deliveryLatitude = deliveryLatitude;
        this.deliveryLongitude = deliveryLongitude;
        this.requestedSlotStart = requestedSlotStart;
        this.requestedSlotEnd = requestedSlotEnd;
        this.reservationExpiresAt = reservationExpiresAt;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void transitionStatus(String toStatus, String cancellationReasonIfCancelled, Instant now) {
        this.status = toStatus;
        if ("CANCELLED".equals(toStatus)) {
            this.cancellationReason = cancellationReasonIfCancelled;
        }
        this.updatedAt = now;
    }

    public void markPaidAndConfirmed(Instant now) {
        this.paymentStatus = "PAID";
        if ("PENDING_PAYMENT".equals(this.status)) {
            this.status = "CONFIRMED";
        }
        this.updatedAt = now;
    }

    public void markPaymentStatus(String newPaymentStatus, Instant now) {
        this.paymentStatus = newPaymentStatus;
        this.updatedAt = now;
    }

    public void reassignBranch(UUID newProcessingBranchId, Instant now) {
        this.processingBranchId = newProcessingBranchId;
        this.updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public String getOrderNumber() {
        return orderNumber;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public UUID getProcessingBranchId() {
        return processingBranchId;
    }

    public String getFulfilmentType() {
        return fulfilmentType;
    }

    public String getStatus() {
        return status;
    }

    public String getPaymentStatus() {
        return paymentStatus;
    }

    public String getCurrencyCode() {
        return currencyCode;
    }

    public BigDecimal getItemsSubtotal() {
        return itemsSubtotal;
    }

    public BigDecimal getDeliveryFee() {
        return deliveryFee;
    }

    public BigDecimal getDiscountAmount() {
        return discountAmount;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public String getRecipientName() {
        return recipientName;
    }

    public String getRecipientPhone() {
        return recipientPhone;
    }

    public String getDeliveryStreetAddress() {
        return deliveryStreetAddress;
    }

    public String getDeliveryDistrict() {
        return deliveryDistrict;
    }

    public String getDeliveryCity() {
        return deliveryCity;
    }

    public BigDecimal getDeliveryLatitude() {
        return deliveryLatitude;
    }

    public BigDecimal getDeliveryLongitude() {
        return deliveryLongitude;
    }

    public Instant getRequestedSlotStart() {
        return requestedSlotStart;
    }

    public Instant getRequestedSlotEnd() {
        return requestedSlotEnd;
    }

    public Instant getReservationExpiresAt() {
        return reservationExpiresAt;
    }

    public String getCancellationReason() {
        return cancellationReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
