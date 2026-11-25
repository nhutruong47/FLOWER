package com.flowerchain.order.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface OrderApi {

    record OrderLineSnapshotDto(
            UUID productId,
            String skuSnapshot,
            String productNameSnapshot,
            String unitPriceSnapshot,
            int quantity,
            String lineTotalSnapshot) {}

    record OrderDetailDto(
            UUID id,
            String orderNumber,
            UUID customerId,
            UUID processingBranchId,
            String fulfilmentType,
            String status,
            String paymentStatus,
            String itemsSubtotal,
            String deliveryFee,
            String discountAmount,
            String totalAmount,
            String currencyCode,
            String recipientName,
            String recipientPhone,
            String deliveryStreetAddress,
            String deliveryDistrict,
            String deliveryCity,
            List<OrderLineSnapshotDto> lines,
            Instant createdAt,
            Instant updatedAt) {}

    record OrderForPaymentDto(
            UUID orderId,
            String orderNumber,
            UUID customerId,
            UUID processingBranchId,
            String status,
            String paymentStatus,
            BigDecimal totalAmount,
            String currencyCode) {}

    record OrderPlacedEvent(
            UUID orderId,
            String orderNumber,
            UUID customerId,
            UUID processingBranchId,
            String fulfilmentType,
            String totalAmount) {}

    record OrderStatusChangedEvent(
            UUID orderId,
            String orderNumber,
            UUID customerId,
            UUID processingBranchId,
            String fromStatus,
            String toStatus) {}

    /** Result of applying a server-verified payment capture to an order. */
    enum VerifiedPaymentApplication {
        /** The order was {@code PENDING_PAYMENT}/{@code UNPAID} and is now {@code CONFIRMED}/{@code PAID}. */
        APPLIED,
        /** The order was in any other state (cancelled, already paid, ...); nothing was changed or written. */
        IGNORED_STALE
    }

    OrderForPaymentDto getOrderForPaymentOrThrow(UUID orderId);

    /**
     * Locks the order row ({@code SELECT ... FOR UPDATE}) for the caller's transaction and returns its payment
     * view, so that all verified payment events of one order are processed one after another. Must be called
     * inside an existing transaction; the lock is held until that transaction ends.
     *
     * @return the order, or empty if no order has this ID (never throws for a missing order, so the caller's
     *     transaction is not marked rollback-only)
     */
    Optional<OrderForPaymentDto> lockOrderForPaymentProcessing(UUID orderId);

    /**
     * Marks the order paid and confirmed after a server-verified payment capture (REQ-009). Only an order in
     * {@code PENDING_PAYMENT} with payment status {@code UNPAID} is changed; for any other state the call
     * writes nothing and returns {@link VerifiedPaymentApplication#IGNORED_STALE}.
     */
    VerifiedPaymentApplication markOrderPaidByVerifiedWebhook(UUID orderId, UUID paymentId, String providerTransactionId);

    void recordOrderRefunded(UUID orderId, BigDecimal refundedAmount, boolean fullRefund, UUID staffActorId, String reason);

    List<OrderDetailDto> findOrdersForRevenueReport(Set<UUID> branchIds, Instant from, Instant to);
}
