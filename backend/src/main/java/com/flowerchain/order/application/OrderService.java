package com.flowerchain.order.application;

import com.flowerchain.audit.api.AuditApi;
import com.flowerchain.audit.api.AuditEntryCommand;
import com.flowerchain.branch.api.BranchApi;
import com.flowerchain.branch.api.BranchDto;
import com.flowerchain.catalog.api.CatalogApi;
import com.flowerchain.catalog.api.ProductSnapshotForBranch;
import com.flowerchain.customer.api.CustomerApi;
import com.flowerchain.inventory.api.InventoryApi;
import com.flowerchain.order.api.OrderApi;
import com.flowerchain.order.internal.OrderEntity;
import com.flowerchain.order.internal.OrderLineEntity;
import com.flowerchain.order.internal.OrderLineRepository;
import com.flowerchain.order.internal.OrderRepository;
import com.flowerchain.order.internal.OrderStatusHistoryEntity;
import com.flowerchain.order.internal.OrderStatusHistoryRepository;
import com.flowerchain.shared.api.FlowerChainException;
import com.flowerchain.shared.api.PageResponse;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService implements OrderApi {

    private static final BigDecimal DEFAULT_DELIVERY_FEE = new BigDecimal("35000.00");
    private static final int REPORT_LINE_LOOKUP_CHUNK = 1000;

    private final OrderRepository orderRepository;
    private final OrderLineRepository orderLineRepository;
    private final OrderStatusHistoryRepository statusHistoryRepository;
    private final BranchApi branchApi;
    private final CustomerApi customerApi;
    private final CatalogApi catalogApi;
    private final InventoryApi inventoryApi;
    private final AuditApi auditApi;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public OrderService(
            OrderRepository orderRepository,
            OrderLineRepository orderLineRepository,
            OrderStatusHistoryRepository statusHistoryRepository,
            BranchApi branchApi,
            CustomerApi customerApi,
            CatalogApi catalogApi,
            InventoryApi inventoryApi,
            AuditApi auditApi,
            ApplicationEventPublisher eventPublisher,
            Clock clock) {
        this.orderRepository = orderRepository;
        this.orderLineRepository = orderLineRepository;
        this.statusHistoryRepository = statusHistoryRepository;
        this.branchApi = branchApi;
        this.customerApi = customerApi;
        this.catalogApi = catalogApi;
        this.inventoryApi = inventoryApi;
        this.auditApi = auditApi;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    public record OrderItemInputDto(UUID productId, int quantity) {}

    public record OrderQuoteDto(
            UUID processingBranchId,
            String processingBranchName,
            boolean priceChangedFromBrowsingBranch,
            String itemsSubtotal,
            String deliveryFee,
            String totalAmount,
            String currencyCode,
            List<OrderLineSnapshotDto> lines) {}

    @Transactional(readOnly = true)
    public OrderQuoteDto quoteOrder(
            UUID browsingBranchId,
            String fulfilmentType,
            UUID pickupBranchId,
            Double deliveryLatitude,
            Double deliveryLongitude,
            List<OrderItemInputDto> items) {
        Map<UUID, Integer> requestedQtyByProduct = aggregateItems(items);
        BranchDto resolvedBranch = resolveProcessingBranch(
                fulfilmentType, pickupBranchId, deliveryLatitude, deliveryLongitude, requestedQtyByProduct);

        List<OrderLineSnapshotDto> lineSnapshots = new ArrayList<>();
        BigDecimal itemsSubtotal = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        boolean priceChanged = false;

        for (Map.Entry<UUID, Integer> entry : requestedQtyByProduct.entrySet()) {
            UUID productId = entry.getKey();
            int qty = entry.getValue();
            ProductSnapshotForBranch snapshot =
                    catalogApi.getSellableProductSnapshotForBranchOrThrow(resolvedBranch.id(), productId);
            BigDecimal lineTotal = snapshot.unitPrice()
                    .multiply(BigDecimal.valueOf(qty))
                    .setScale(2, RoundingMode.HALF_UP);
            itemsSubtotal = itemsSubtotal.add(lineTotal);
            lineSnapshots.add(new OrderLineSnapshotDto(
                    productId,
                    snapshot.sku(),
                    snapshot.productName(),
                    snapshot.unitPrice().toPlainString(),
                    qty,
                    lineTotal.toPlainString()));

            if (browsingBranchId != null && !browsingBranchId.equals(resolvedBranch.id())) {
                try {
                    ProductSnapshotForBranch browsingSnap =
                            catalogApi.getSellableProductSnapshotForBranchOrThrow(browsingBranchId, productId);
                    if (browsingSnap.unitPrice().compareTo(snapshot.unitPrice()) != 0) {
                        priceChanged = true;
                    }
                } catch (FlowerChainException ignored) {
                    priceChanged = true;
                }
            }
        }

        BigDecimal deliveryFee = "DELIVERY".equalsIgnoreCase(fulfilmentType)
                ? DEFAULT_DELIVERY_FEE
                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalAmount = itemsSubtotal.add(deliveryFee);

        return new OrderQuoteDto(
                resolvedBranch.id(),
                resolvedBranch.name(),
                priceChanged,
                itemsSubtotal.toPlainString(),
                deliveryFee.toPlainString(),
                totalAmount.toPlainString(),
                "VND",
                lineSnapshots);
    }

    @Transactional
    public OrderDetailDto placeOrder(
            UUID customerId,
            String fulfilmentType,
            UUID pickupBranchId,
            String paymentMethod,
            String recipientName,
            String recipientPhone,
            String deliveryStreetAddress,
            String deliveryDistrict,
            String deliveryCity,
            Double deliveryLatitude,
            Double deliveryLongitude,
            Instant requestedSlotStart,
            Instant requestedSlotEnd,
            String confirmedTotalAmount,
            List<OrderItemInputDto> items) {
        // A disabled customer must not place orders, even with a token issued before the account was disabled.
        customerApi.getActiveCustomerOrThrow(customerId);
        String normalizedFulfilment = fulfilmentType.trim().toUpperCase(Locale.ROOT);
        if ("DELIVERY".equals(normalizedFulfilment)) {
            if (deliveryStreetAddress == null || deliveryStreetAddress.isBlank()
                    || deliveryDistrict == null || deliveryDistrict.isBlank()
                    || deliveryCity == null || deliveryCity.isBlank()) {
                throw FlowerChainException.badRequest(
                        "DELIVERY_ADDRESS_REQUIRED",
                        "Delivery street address, district, and city are required for DELIVERY orders.");
            }
        }

        Map<UUID, Integer> requestedQtyByProduct = aggregateItems(items);
        BranchDto processingBranch = resolveProcessingBranch(
                normalizedFulfilment, pickupBranchId, deliveryLatitude, deliveryLongitude, requestedQtyByProduct);

        List<OrderLineEntity> orderLineEntities = new ArrayList<>();
        BigDecimal itemsSubtotal = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        Instant now = Instant.now(clock);
        UUID orderId = UUID.randomUUID();

        for (Map.Entry<UUID, Integer> entry : requestedQtyByProduct.entrySet()) {
            UUID productId = entry.getKey();
            int qty = entry.getValue();
            ProductSnapshotForBranch snapshot =
                    catalogApi.getSellableProductSnapshotForBranchOrThrow(processingBranch.id(), productId);
            BigDecimal lineTotal = snapshot.unitPrice()
                    .multiply(BigDecimal.valueOf(qty))
                    .setScale(2, RoundingMode.HALF_UP);
            itemsSubtotal = itemsSubtotal.add(lineTotal);
            orderLineEntities.add(new OrderLineEntity(
                    UUID.randomUUID(),
                    orderId,
                    productId,
                    snapshot.sku(),
                    snapshot.productName(),
                    snapshot.unitPrice(),
                    qty,
                    lineTotal,
                    now));
        }

        BigDecimal deliveryFee = "DELIVERY".equals(normalizedFulfilment)
                ? DEFAULT_DELIVERY_FEE
                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal discountAmount = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalAmount = itemsSubtotal.add(deliveryFee).subtract(discountAmount);

        if (confirmedTotalAmount != null && !confirmedTotalAmount.isBlank()) {
            BigDecimal confirmed;
            try {
                confirmed = new BigDecimal(confirmedTotalAmount.trim()).setScale(2, RoundingMode.HALF_UP);
            } catch (NumberFormatException e) {
                throw FlowerChainException.badRequest("INVALID_CONFIRMED_TOTAL", "Invalid confirmedTotalAmount format.");
            }
            if (confirmed.compareTo(totalAmount) != 0) {
                throw FlowerChainException.conflict(
                        "ORDER_PRICE_MISMATCH",
                        "Order total changed for allocated branch",
                        "The calculated order total at branch " + processingBranch.name()
                                + " is " + totalAmount.toPlainString()
                                + " (expected " + confirmed.toPlainString() + "). Please review the quote.");
            }
        }

        String normalizedPaymentMethod = paymentMethod.trim().toUpperCase(Locale.ROOT);
        String initialStatus = "ONLINE_GATEWAY".equals(normalizedPaymentMethod) ? "PENDING_PAYMENT" : "CONFIRMED";
        Instant reservationExpiresAt = "PENDING_PAYMENT".equals(initialStatus)
                ? now.plus(Duration.ofMinutes(15))
                : null;
        String orderNumber = "ORD-" + orderRepository.nextOrderNumber();

        OrderEntity savedOrder = orderRepository.save(new OrderEntity(
                orderId,
                orderNumber,
                customerId,
                processingBranch.id(),
                normalizedFulfilment,
                initialStatus,
                "UNPAID",
                "VND",
                itemsSubtotal,
                deliveryFee,
                discountAmount,
                totalAmount,
                recipientName.trim(),
                recipientPhone.trim(),
                deliveryStreetAddress,
                deliveryDistrict,
                deliveryCity,
                deliveryLatitude != null ? BigDecimal.valueOf(deliveryLatitude) : null,
                deliveryLongitude != null ? BigDecimal.valueOf(deliveryLongitude) : null,
                requestedSlotStart,
                requestedSlotEnd,
                reservationExpiresAt,
                now));

        List<OrderLineEntity> savedLines = orderLineRepository.saveAll(orderLineEntities);

        // Two-phase stock reservation (REQ-007, REQ-008): reserve stock immediately upon order creation
        inventoryApi.reserveStockForOrder(
                processingBranch.id(),
                orderId,
                requestedQtyByProduct,
                customerId,
                "CUSTOMER",
                "Reserved for order " + orderNumber);

        statusHistoryRepository.save(new OrderStatusHistoryEntity(
                UUID.randomUUID(),
                orderId,
                null,
                initialStatus,
                null,
                processingBranch.id(),
                customerId,
                "CUSTOMER",
                "Order placed",
                now));

        auditApi.record(new AuditEntryCommand(
                "ORDER_STATUS",
                "ORDER_PLACED",
                processingBranch.id(),
                "ORDER",
                orderId,
                customerId,
                "CUSTOMER",
                null,
                "{\"status\":\"" + initialStatus + "\",\"totalAmount\":\"" + totalAmount.toPlainString() + "\"}"));

        eventPublisher.publishEvent(new OrderPlacedEvent(
                orderId,
                orderNumber,
                customerId,
                processingBranch.id(),
                normalizedFulfilment,
                totalAmount.toPlainString()));

        return toDetailDto(savedOrder, savedLines);
    }

    @Transactional(readOnly = true)
    public PageResponse<OrderDetailDto> listCustomerOrders(UUID customerId, int page, int size) {
        List<OrderDetailDto> items = orderRepository.findByCustomerIdOrderByCreatedAtDesc(customerId).stream()
                .map(o -> toDetailDto(o, orderLineRepository.findByOrderId(o.getId())))
                .toList();
        return PageResponse.of(items, page, size);
    }

    @Transactional(readOnly = true)
    public OrderDetailDto getCustomerOrderOrThrow404(UUID customerId, UUID orderId) {
        OrderEntity order = orderRepository
                .findById(orderId)
                .filter(o -> o.getCustomerId().equals(customerId))
                .orElseThrow(() -> FlowerChainException.notFound("Order " + orderId + " not found."));
        return toDetailDto(order, orderLineRepository.findByOrderId(orderId));
    }

    @Transactional
    public OrderDetailDto cancelCustomerOrder(UUID customerId, UUID orderId, String reason) {
        OrderEntity order = orderRepository
                .findById(orderId)
                .filter(o -> o.getCustomerId().equals(customerId))
                .orElseThrow(() -> FlowerChainException.notFound("Order " + orderId + " not found."));

        if (!"PENDING_PAYMENT".equals(order.getStatus()) && !"CONFIRMED".equals(order.getStatus())) {
            throw FlowerChainException.conflict(
                    "ORDER_CANNOT_BE_CANCELLED",
                    "Order cannot be cancelled",
                    "Order in status " + order.getStatus() + " has already entered preparation and cannot be cancelled.");
        }

        String previousStatus = order.getStatus();
        Instant now = Instant.now(clock);
        List<OrderLineEntity> lines = orderLineRepository.findByOrderId(orderId);
        Map<UUID, Integer> qtyMap = lines.stream()
                .collect(Collectors.toMap(OrderLineEntity::getProductId, OrderLineEntity::getQuantity));

        inventoryApi.releaseOrderStock(
                order.getProcessingBranchId(),
                orderId,
                qtyMap,
                customerId,
                "CUSTOMER",
                "Customer cancelled order: " + reason);

        order.transitionStatus("CANCELLED", reason.trim(), now);
        OrderEntity saved = orderRepository.save(order);

        statusHistoryRepository.save(new OrderStatusHistoryEntity(
                UUID.randomUUID(),
                orderId,
                previousStatus,
                "CANCELLED",
                order.getProcessingBranchId(),
                order.getProcessingBranchId(),
                customerId,
                "CUSTOMER",
                reason.trim(),
                now));

        auditApi.record(new AuditEntryCommand(
                "ORDER_STATUS",
                "ORDER_CANCELLED_BY_CUSTOMER",
                order.getProcessingBranchId(),
                "ORDER",
                orderId,
                customerId,
                "CUSTOMER",
                "{\"status\":\"" + previousStatus + "\"}",
                "{\"status\":\"CANCELLED\",\"reason\":\"" + sanitizeJson(reason) + "\"}"));

        eventPublisher.publishEvent(new OrderStatusChangedEvent(
                orderId,
                order.getOrderNumber(),
                customerId,
                order.getProcessingBranchId(),
                previousStatus,
                "CANCELLED"));

        return toDetailDto(saved, lines);
    }

    @Transactional(readOnly = true)
    public PageResponse<OrderDetailDto> listAdminOrders(
            Set<UUID> effectiveBranchIds, String statusFilter, String fulfilmentTypeFilter, int page, int size) {
        List<OrderDetailDto> items = orderRepository.findAllByOrderByCreatedAtDesc().stream()
                .filter(o -> effectiveBranchIds.contains(o.getProcessingBranchId()))
                .filter(o -> statusFilter == null || statusFilter.isBlank() || statusFilter.equalsIgnoreCase(o.getStatus()))
                .filter(o -> fulfilmentTypeFilter == null
                        || fulfilmentTypeFilter.isBlank()
                        || fulfilmentTypeFilter.equalsIgnoreCase(o.getFulfilmentType()))
                .map(o -> toDetailDto(o, orderLineRepository.findByOrderId(o.getId())))
                .toList();
        return PageResponse.of(items, page, size);
    }

    @Transactional(readOnly = true)
    public OrderDetailDto getAdminOrderOrThrow(
            UUID orderId, boolean callerHasGlobalAccess, Set<UUID> callerAuthorizedBranchIds) {
        OrderEntity order = orderRepository
                .findById(orderId)
                .orElseThrow(() -> FlowerChainException.notFound("Order " + orderId + " not found."));
        if (!callerHasGlobalAccess
                && (callerAuthorizedBranchIds == null || !callerAuthorizedBranchIds.contains(order.getProcessingBranchId()))) {
            throw FlowerChainException.forbidden(
                    "BRANCH_ACCESS_DENIED",
                    "You are not authorized to access order " + orderId + " belonging to branch "
                            + order.getProcessingBranchId() + ".");
        }
        return toDetailDto(order, orderLineRepository.findByOrderId(orderId));
    }

    @Transactional
    public OrderDetailDto transitionOrderStatusByStaff(
            UUID orderId,
            String toStatusRaw,
            String note,
            UUID staffActorId,
            boolean callerHasGlobalAccess,
            Set<UUID> callerAuthorizedBranchIds) {
        OrderEntity order = orderRepository
                .findById(orderId)
                .orElseThrow(() -> FlowerChainException.notFound("Order " + orderId + " not found."));
        if (!callerHasGlobalAccess
                && (callerAuthorizedBranchIds == null || !callerAuthorizedBranchIds.contains(order.getProcessingBranchId()))) {
            throw FlowerChainException.forbidden(
                    "BRANCH_ACCESS_DENIED",
                    "You are not authorized to modify order " + orderId + " belonging to branch "
                            + order.getProcessingBranchId() + ".");
        }

        String fromStatus = order.getStatus();
        String toStatus = toStatusRaw.trim().toUpperCase(Locale.ROOT);
        validateStatusTransition(fromStatus, toStatus, order.getFulfilmentType());

        List<OrderLineEntity> lines = orderLineRepository.findByOrderId(orderId);
        Map<UUID, Integer> qtyMap = lines.stream()
                .collect(Collectors.toMap(OrderLineEntity::getProductId, OrderLineEntity::getQuantity));

        if ("PREPARING".equals(toStatus) && ("CONFIRMED".equals(fromStatus) || "PENDING_PAYMENT".equals(fromStatus))) {
            inventoryApi.confirmOrderStockDeduction(
                    order.getProcessingBranchId(),
                    orderId,
                    qtyMap,
                    staffActorId,
                    "STAFF",
                    note != null ? note : "Stock confirmed as order entered PREPARING");
        } else if ("CANCELLED".equals(toStatus) && ("PENDING_PAYMENT".equals(fromStatus) || "CONFIRMED".equals(fromStatus))) {
            inventoryApi.releaseOrderStock(
                    order.getProcessingBranchId(),
                    orderId,
                    qtyMap,
                    staffActorId,
                    "STAFF",
                    note != null ? note : "Stock released as order was cancelled before preparation");
        }

        Instant now = Instant.now(clock);
        order.transitionStatus(toStatus, "CANCELLED".equals(toStatus) ? note : null, now);
        OrderEntity saved = orderRepository.save(order);

        statusHistoryRepository.save(new OrderStatusHistoryEntity(
                UUID.randomUUID(),
                orderId,
                fromStatus,
                toStatus,
                order.getProcessingBranchId(),
                order.getProcessingBranchId(),
                staffActorId,
                "STAFF",
                note,
                now));

        auditApi.record(new AuditEntryCommand(
                "ORDER_STATUS",
                "ORDER_STATUS_TRANSITIONED",
                order.getProcessingBranchId(),
                "ORDER",
                orderId,
                staffActorId,
                "STAFF",
                "{\"status\":\"" + fromStatus + "\"}",
                "{\"status\":\"" + toStatus + "\"}"));

        eventPublisher.publishEvent(new OrderStatusChangedEvent(
                orderId,
                order.getOrderNumber(),
                order.getCustomerId(),
                order.getProcessingBranchId(),
                fromStatus,
                toStatus));

        return toDetailDto(saved, lines);
    }

    @Transactional
    public OrderDetailDto reassignOrderBranch(
            UUID orderId,
            UUID newProcessingBranchId,
            String reason,
            UUID staffActorId) {
        OrderEntity order = orderRepository
                .findById(orderId)
                .orElseThrow(() -> FlowerChainException.notFound("Order " + orderId + " not found."));

        if (!"PENDING_PAYMENT".equals(order.getStatus()) && !"CONFIRMED".equals(order.getStatus())) {
            throw FlowerChainException.conflict(
                    "ORDER_REASSIGNMENT_NOT_ALLOWED",
                    "Order cannot be reassigned",
                    "Orders in status " + order.getStatus() + " have already entered preparation and cannot be reassigned.");
        }

        UUID oldBranchId = order.getProcessingBranchId();
        if (oldBranchId.equals(newProcessingBranchId)) {
            throw FlowerChainException.badRequest(
                    "SAME_PROCESSING_BRANCH", "New processing branch must differ from current processing branch.");
        }
        branchApi.getActiveBranchOrThrow(newProcessingBranchId);

        List<OrderLineEntity> lines = orderLineRepository.findByOrderId(orderId);
        Map<UUID, Integer> qtyMap = lines.stream()
                .collect(Collectors.toMap(OrderLineEntity::getProductId, OrderLineEntity::getQuantity));

        // Release reserved stock at old branch and reserve at new branch (preserving order line price snapshots per REQ-006)
        inventoryApi.releaseOrderStock(
                oldBranchId,
                orderId,
                qtyMap,
                staffActorId,
                "STAFF",
                "Order reassigned from branch " + oldBranchId + " to " + newProcessingBranchId + ": " + reason);

        inventoryApi.reserveStockForOrder(
                newProcessingBranchId,
                orderId,
                qtyMap,
                staffActorId,
                "STAFF",
                "Order reassigned from branch " + oldBranchId + " to " + newProcessingBranchId + ": " + reason);

        Instant now = Instant.now(clock);
        order.reassignBranch(newProcessingBranchId, now);
        OrderEntity saved = orderRepository.save(order);

        statusHistoryRepository.save(new OrderStatusHistoryEntity(
                UUID.randomUUID(),
                orderId,
                order.getStatus(),
                order.getStatus(),
                oldBranchId,
                newProcessingBranchId,
                staffActorId,
                "STAFF",
                reason.trim(),
                now));

        auditApi.record(new AuditEntryCommand(
                "ORDER_STATUS",
                "ORDER_BRANCH_REASSIGNED",
                newProcessingBranchId,
                "ORDER",
                orderId,
                staffActorId,
                "STAFF",
                "{\"processingBranchId\":\"" + oldBranchId + "\"}",
                "{\"processingBranchId\":\"" + newProcessingBranchId + "\",\"reason\":\"" + sanitizeJson(reason) + "\"}"));

        return toDetailDto(saved, lines);
    }

    @Override
    @Transactional(readOnly = true)
    public OrderForPaymentDto getOrderForPaymentOrThrow(UUID orderId) {
        OrderEntity order = orderRepository
                .findById(orderId)
                .orElseThrow(() -> FlowerChainException.notFound("Order " + orderId + " not found."));
        return new OrderForPaymentDto(
                order.getId(),
                order.getOrderNumber(),
                order.getCustomerId(),
                order.getProcessingBranchId(),
                order.getStatus(),
                order.getPaymentStatus(),
                order.getTotalAmount(),
                order.getCurrencyCode());
    }

    @Override
    @Transactional
    public void markOrderPaidByVerifiedWebhook(UUID orderId, UUID paymentId, String providerTransactionId) {
        OrderEntity order = orderRepository
                .findById(orderId)
                .orElseThrow(() -> FlowerChainException.notFound("Order " + orderId + " not found."));
        String fromStatus = order.getStatus();
        Instant now = Instant.now(clock);
        order.markPaidAndConfirmed(now);
        orderRepository.save(order);

        statusHistoryRepository.save(new OrderStatusHistoryEntity(
                UUID.randomUUID(),
                orderId,
                fromStatus,
                order.getStatus(),
                order.getProcessingBranchId(),
                order.getProcessingBranchId(),
                paymentId,
                "WEBHOOK",
                "Verified payment webhook transaction " + providerTransactionId,
                now));

        auditApi.record(new AuditEntryCommand(
                "ORDER_STATUS",
                "ORDER_PAID_WEBHOOK",
                order.getProcessingBranchId(),
                "ORDER",
                orderId,
                paymentId,
                "WEBHOOK",
                "{\"status\":\"" + fromStatus + "\",\"paymentStatus\":\"UNPAID\"}",
                "{\"status\":\"" + order.getStatus() + "\",\"paymentStatus\":\"PAID\"}"));

        eventPublisher.publishEvent(new OrderStatusChangedEvent(
                orderId,
                order.getOrderNumber(),
                order.getCustomerId(),
                order.getProcessingBranchId(),
                fromStatus,
                order.getStatus()));
    }

    @Override
    @Transactional
    public void recordOrderRefunded(
            UUID orderId, BigDecimal refundedAmount, boolean fullRefund, UUID staffActorId, String reason) {
        OrderEntity order = orderRepository
                .findById(orderId)
                .orElseThrow(() -> FlowerChainException.notFound("Order " + orderId + " not found."));
        String oldPaymentStatus = order.getPaymentStatus();
        String newPaymentStatus = fullRefund ? "REFUNDED" : "PARTIALLY_REFUNDED";
        order.markPaymentStatus(newPaymentStatus, Instant.now(clock));
        orderRepository.save(order);

        auditApi.record(new AuditEntryCommand(
                "REFUND",
                "ORDER_REFUND_RECORDED",
                order.getProcessingBranchId(),
                "ORDER",
                orderId,
                staffActorId,
                "STAFF",
                "{\"paymentStatus\":\"" + oldPaymentStatus + "\"}",
                "{\"paymentStatus\":\"" + newPaymentStatus + "\",\"refundAmount\":\"" + refundedAmount.toPlainString() + "\"}"));
    }

    /**
     * Orders processed by one of {@code branchIds} and created in the half-open range {@code [from, to)}, with
     * their persisted line snapshots (REQ-005, REQ-006). The half-open range lets adjacent report periods share
     * a boundary without counting an order twice. Status filtering is left to the caller.
     */
    @Override
    @Transactional(readOnly = true)
    public List<OrderDetailDto> findOrdersForRevenueReport(Set<UUID> branchIds, Instant from, Instant to) {
        if (branchIds == null || branchIds.isEmpty() || from == null || to == null || !from.isBefore(to)) {
            return List.of();
        }
        List<OrderEntity> orders = orderRepository
                .findByProcessingBranchIdInAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtAsc(
                        branchIds, from, to);
        if (orders.isEmpty()) {
            return List.of();
        }
        List<UUID> orderIds = orders.stream().map(OrderEntity::getId).toList();
        Map<UUID, List<OrderLineEntity>> linesByOrder = new LinkedHashMap<>();
        // Chunked so a long report period never exceeds PostgreSQL's bind-parameter limit.
        for (int start = 0; start < orderIds.size(); start += REPORT_LINE_LOOKUP_CHUNK) {
            List<UUID> chunk = orderIds.subList(start, Math.min(start + REPORT_LINE_LOOKUP_CHUNK, orderIds.size()));
            for (OrderLineEntity line : orderLineRepository.findByOrderIdIn(chunk)) {
                linesByOrder.computeIfAbsent(line.getOrderId(), id -> new ArrayList<>()).add(line);
            }
        }
        return orders.stream()
                .map(o -> toDetailDto(o, linesByOrder.getOrDefault(o.getId(), List.of())))
                .toList();
    }

    private BranchDto resolveProcessingBranch(
            String fulfilmentType,
            UUID pickupBranchId,
            Double deliveryLatitude,
            Double deliveryLongitude,
            Map<UUID, Integer> requestedQtyByProduct) {
        String normalized = fulfilmentType.trim().toUpperCase(Locale.ROOT);
        if ("PICKUP".equals(normalized)) {
            if (pickupBranchId == null) {
                throw FlowerChainException.badRequest(
                        "PICKUP_BRANCH_REQUIRED", "pickupBranchId is required when fulfilmentType is PICKUP.");
            }
            BranchDto branch = branchApi.getActiveBranchOrThrow(pickupBranchId);
            if (!inventoryApi.hasSufficientStock(branch.id(), requestedQtyByProduct)) {
                throw FlowerChainException.conflict(
                        "INVENTORY_INSUFFICIENT_STOCK",
                        "Insufficient branch inventory",
                        "Selected pickup branch " + branch.name() + " does not have sufficient stock for all items.");
            }
            return branch;
        }

        if ("DELIVERY".equals(normalized)) {
            if (deliveryLatitude == null || deliveryLongitude == null) {
                throw FlowerChainException.badRequest(
                        "DELIVERY_COORDINATES_REQUIRED",
                        "deliveryLatitude and deliveryLongitude are required for automatic nearest-branch allocation.");
            }
            List<BranchDto> coveringBranches = branchApi
                    .findActiveBranchesCoveringLocationOrderedByDistance(deliveryLatitude, deliveryLongitude);
            if (coveringBranches.isEmpty()) {
                throw FlowerChainException.unprocessable(
                        "DELIVERY_OUT_OF_COVERAGE",
                        "Delivery address outside coverage area",
                        "No active flower shop branch covers the requested delivery coordinates.");
            }
            List<UUID> candidateIds = coveringBranches.stream().map(BranchDto::id).toList();
            List<UUID> stockedIds =
                    inventoryApi.findBranchesWithSufficientStock(candidateIds, requestedQtyByProduct);

            // Also ensure branch has sellable prices configured for all requested products
            for (UUID branchId : stockedIds) {
                boolean allSellable = true;
                for (UUID productId : requestedQtyByProduct.keySet()) {
                    try {
                        catalogApi.getSellableProductSnapshotForBranchOrThrow(branchId, productId);
                    } catch (FlowerChainException ex) {
                        allSellable = false;
                        break;
                    }
                }
                if (allSellable) {
                    return coveringBranches.stream()
                            .filter(b -> b.id().equals(branchId))
                            .findFirst()
                            .orElseThrow();
                }
            }

            throw FlowerChainException.conflict(
                    "INVENTORY_INSUFFICIENT_STOCK",
                    "Insufficient branch inventory",
                    "No active branch covering the delivery address holds sufficient available stock for the requested items.");
        }

        throw FlowerChainException.badRequest("INVALID_FULFILMENT_TYPE", "fulfilmentType must be DELIVERY or PICKUP.");
    }

    private Map<UUID, Integer> aggregateItems(List<OrderItemInputDto> items) {
        if (items == null || items.isEmpty()) {
            throw FlowerChainException.badRequest("EMPTY_ORDER_ITEMS", "Order must contain at least one item.");
        }
        Map<UUID, Integer> map = new LinkedHashMap<>();
        for (OrderItemInputDto item : items) {
            if (item.productId() == null || item.quantity() <= 0) {
                throw FlowerChainException.badRequest(
                        "INVALID_ORDER_ITEM", "Each order item must specify a productId and a positive quantity.");
            }
            map.merge(item.productId(), item.quantity(), Integer::sum);
        }
        return map;
    }

    private void validateStatusTransition(String fromStatus, String toStatus, String fulfilmentType) {
        if (fromStatus.equals(toStatus)) {
            throw FlowerChainException.conflict(
                    "INVALID_ORDER_TRANSITION",
                    "Order is already in status " + toStatus,
                    "Order is already in status " + toStatus + ".");
        }
        if ("COMPLETED".equals(fromStatus) || "CANCELLED".equals(fromStatus)) {
            throw FlowerChainException.conflict(
                    "INVALID_ORDER_TRANSITION",
                    "Terminal order status",
                    "Order in terminal status " + fromStatus + " cannot transition to " + toStatus + ".");
        }
        if ("READY_FOR_PICKUP".equals(toStatus) && !"PICKUP".equals(fulfilmentType)) {
            throw FlowerChainException.conflict(
                    "INVALID_ORDER_TRANSITION",
                    "Invalid transition for DELIVERY order",
                    "DELIVERY orders cannot transition to READY_FOR_PICKUP.");
        }
        if (("READY_FOR_DELIVERY".equals(toStatus) || "OUT_FOR_DELIVERY".equals(toStatus))
                && !"DELIVERY".equals(fulfilmentType)) {
            throw FlowerChainException.conflict(
                    "INVALID_ORDER_TRANSITION",
                    "Invalid transition for PICKUP order",
                    "PICKUP orders cannot transition to " + toStatus + ".");
        }
        if ("CANCELLED".equals(toStatus)
                && !"PENDING_PAYMENT".equals(fromStatus)
                && !"CONFIRMED".equals(fromStatus)) {
            throw FlowerChainException.conflict(
                    "ORDER_CANNOT_BE_CANCELLED",
                    "Order cannot be cancelled after preparation",
                    "Order in status " + fromStatus + " cannot be cancelled.");
        }
    }

    private OrderDetailDto toDetailDto(OrderEntity order, List<OrderLineEntity> lines) {
        List<OrderLineSnapshotDto> lineDtos = lines.stream()
                .map(l -> new OrderLineSnapshotDto(
                        l.getProductId(),
                        l.getSkuSnapshot(),
                        l.getProductNameSnapshot(),
                        l.getUnitPriceSnapshot().toPlainString(),
                        l.getQuantity(),
                        l.getLineTotalSnapshot().toPlainString()))
                .toList();
        return new OrderDetailDto(
                order.getId(),
                order.getOrderNumber(),
                order.getCustomerId(),
                order.getProcessingBranchId(),
                order.getFulfilmentType(),
                order.getStatus(),
                order.getPaymentStatus(),
                order.getItemsSubtotal().toPlainString(),
                order.getDeliveryFee().toPlainString(),
                order.getDiscountAmount().toPlainString(),
                order.getTotalAmount().toPlainString(),
                order.getCurrencyCode(),
                order.getRecipientName(),
                order.getRecipientPhone(),
                order.getDeliveryStreetAddress(),
                order.getDeliveryDistrict(),
                order.getDeliveryCity(),
                lineDtos,
                order.getCreatedAt(),
                order.getUpdatedAt());
    }

    private static String sanitizeJson(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
