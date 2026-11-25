package com.flowerchain.order.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
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

    OrderForPaymentDto getOrderForPaymentOrThrow(UUID orderId);

    void markOrderPaidByVerifiedWebhook(UUID orderId, UUID paymentId, String providerTransactionId);

    void recordOrderRefunded(UUID orderId, BigDecimal refundedAmount, boolean fullRefund, UUID staffActorId, String reason);

    List<OrderDetailDto> findOrdersForRevenueReport(Set<UUID> branchIds, Instant from, Instant to);
}
