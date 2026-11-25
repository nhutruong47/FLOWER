package com.flowerchain.order.web;

import com.flowerchain.branch.api.BranchApi;
import com.flowerchain.branch.api.BranchDto;
import com.flowerchain.order.api.OrderApi.OrderDetailDto;
import com.flowerchain.order.application.OrderService;
import com.flowerchain.order.application.OrderService.OrderItemInputDto;
import com.flowerchain.order.application.OrderService.OrderQuoteDto;
import com.flowerchain.shared.api.BranchAuthorizationGuard;
import com.flowerchain.shared.api.FlowerChainPrincipal;
import com.flowerchain.shared.api.IdempotencyExecutor;
import com.flowerchain.shared.api.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class OrderController {

    private final OrderService orderService;
    private final BranchApi branchApi;
    private final BranchAuthorizationGuard branchGuard;
    private final IdempotencyExecutor idempotencyExecutor;

    public OrderController(
            OrderService orderService,
            BranchApi branchApi,
            BranchAuthorizationGuard branchGuard,
            IdempotencyExecutor idempotencyExecutor) {
        this.orderService = orderService;
        this.branchApi = branchApi;
        this.branchGuard = branchGuard;
        this.idempotencyExecutor = idempotencyExecutor;
    }

    public record OrderItemRequest(@NotNull UUID productId, @Min(1) int quantity) {}

    public record OrderQuoteRequest(
            UUID browsingBranchId,
            @NotBlank @Pattern(regexp = "DELIVERY|PICKUP") String fulfilmentType,
            UUID pickupBranchId,
            @DecimalMin("-90.0") @DecimalMax("90.0") Double deliveryLatitude,
            @DecimalMin("-180.0") @DecimalMax("180.0") Double deliveryLongitude,
            @NotEmpty List<@Valid OrderItemRequest> items) {}

    public record PlaceOrderRequest(
            @NotBlank @Pattern(regexp = "DELIVERY|PICKUP") String fulfilmentType,
            UUID pickupBranchId,
            @NotBlank @Pattern(regexp = "ONLINE_GATEWAY|COD|PAY_AT_PICKUP") String paymentMethod,
            @NotBlank @Size(min = 2, max = 150) String recipientName,
            @NotBlank @Size(min = 8, max = 32) String recipientPhone,
            @Size(max = 255) String deliveryStreetAddress,
            @Size(max = 100) String deliveryDistrict,
            @Size(max = 100) String deliveryCity,
            @DecimalMin("-90.0") @DecimalMax("90.0") Double deliveryLatitude,
            @DecimalMin("-180.0") @DecimalMax("180.0") Double deliveryLongitude,
            Instant requestedSlotStart,
            Instant requestedSlotEnd,
            @NotBlank String confirmedTotalAmount,
            @NotEmpty List<@Valid OrderItemRequest> items) {}

    public record CancelOrderRequest(@NotBlank @Size(min = 3, max = 500) String reason) {}

    public record TransitionOrderStatusRequest(
            @NotBlank
            @Pattern(regexp = "CONFIRMED|PREPARING|READY_FOR_DELIVERY|OUT_FOR_DELIVERY|READY_FOR_PICKUP|COMPLETED|CANCELLED")
            String toStatus,
            @Size(max = 500) String note) {}

    public record ReassignOrderBranchRequest(
            @NotNull UUID newProcessingBranchId,
            @NotBlank @Size(min = 5, max = 500) String reason) {}

    @PostMapping("/api/v1/storefront/orders/quote")
    public OrderQuoteDto quoteStorefrontOrder(@Valid @RequestBody OrderQuoteRequest request) {
        branchGuard.requireCustomerPrincipal();
        List<OrderItemInputDto> items = request.items().stream()
                .map(i -> new OrderItemInputDto(i.productId(), i.quantity()))
                .toList();
        return orderService.quoteOrder(
                request.browsingBranchId(),
                request.fulfilmentType(),
                request.pickupBranchId(),
                request.deliveryLatitude(),
                request.deliveryLongitude(),
                items);
    }

    @PostMapping("/api/v1/storefront/orders")
    @ResponseStatus(HttpStatus.CREATED)
    public OrderDetailDto placeStorefrontOrder(
            @RequestHeader(name = "Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody PlaceOrderRequest request) {
        FlowerChainPrincipal customer = branchGuard.requireCustomerPrincipal();
        String payloadSignature = request.fulfilmentType() + "|"
                + request.pickupBranchId() + "|"
                + request.paymentMethod() + "|"
                + request.confirmedTotalAmount() + "|"
                + request.items().stream()
                        .map(i -> i.productId() + ":" + i.quantity())
                        .collect(Collectors.joining(","));

        return idempotencyExecutor.executeIdempotently(
                idempotencyKey,
                "CUSTOMER:" + customer.actorId(),
                "POST /api/v1/storefront/orders",
                payloadSignature,
                OrderDetailDto.class,
                () -> {
                    List<OrderItemInputDto> items = request.items().stream()
                            .map(i -> new OrderItemInputDto(i.productId(), i.quantity()))
                            .toList();
                    return orderService.placeOrder(
                            customer.actorId(),
                            request.fulfilmentType(),
                            request.pickupBranchId(),
                            request.paymentMethod(),
                            request.recipientName(),
                            request.recipientPhone(),
                            request.deliveryStreetAddress(),
                            request.deliveryDistrict(),
                            request.deliveryCity(),
                            request.deliveryLatitude(),
                            request.deliveryLongitude(),
                            request.requestedSlotStart(),
                            request.requestedSlotEnd(),
                            request.confirmedTotalAmount(),
                            items);
                });
    }

    @GetMapping("/api/v1/storefront/orders")
    public PageResponse<OrderDetailDto> listMyOrders(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        FlowerChainPrincipal customer = branchGuard.requireCustomerPrincipal();
        return orderService.listCustomerOrders(customer.actorId(), page, size);
    }

    @GetMapping("/api/v1/storefront/orders/{orderId}")
    public OrderDetailDto getMyOrder(@PathVariable UUID orderId) {
        FlowerChainPrincipal customer = branchGuard.requireCustomerPrincipal();
        return orderService.getCustomerOrderOrThrow404(customer.actorId(), orderId);
    }

    @PostMapping("/api/v1/storefront/orders/{orderId}/cancel")
    public OrderDetailDto cancelMyOrder(
            @PathVariable UUID orderId, @Valid @RequestBody CancelOrderRequest request) {
        FlowerChainPrincipal customer = branchGuard.requireCustomerPrincipal();
        return orderService.cancelCustomerOrder(customer.actorId(), orderId, request.reason());
    }

    @GetMapping("/api/v1/admin/orders")
    public PageResponse<OrderDetailDto> listAdminOrders(
            @RequestParam(required = false) UUID branchId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String fulfilmentType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Set<UUID> allBranchIds = branchApi.listActiveBranches().stream()
                .map(BranchDto::id)
                .collect(Collectors.toSet());
        Set<UUID> effectiveBranchIds =
                branchGuard.resolveEffectiveBranchFilter(Optional.ofNullable(branchId), allBranchIds);
        return orderService.listAdminOrders(effectiveBranchIds, status, fulfilmentType, page, size);
    }

    @GetMapping("/api/v1/admin/orders/{orderId}")
    public OrderDetailDto getAdminOrder(@PathVariable UUID orderId) {
        FlowerChainPrincipal principal = branchGuard.requireStaffPrincipal();
        return orderService.getAdminOrderOrThrow(
                orderId, principal.globalAccess(), principal.authorizedBranchIds());
    }

    @PostMapping("/api/v1/admin/orders/{orderId}/status")
    public OrderDetailDto transitionOrderStatus(
            @PathVariable UUID orderId,
            @Valid @RequestBody TransitionOrderStatusRequest request) {
        FlowerChainPrincipal principal = branchGuard.requireAnyRole(
                "SYSTEM_OWNER", "HEAD_OFFICE_ADMIN", "BRANCH_MANAGER", "SALES_STAFF", "DELIVERY_STAFF");
        return orderService.transitionOrderStatusByStaff(
                orderId,
                request.toStatus(),
                request.note(),
                principal.actorId(),
                principal.globalAccess(),
                principal.authorizedBranchIds());
    }

    @PostMapping("/api/v1/admin/orders/{orderId}/reassign-branch")
    public OrderDetailDto reassignOrderBranch(
            @PathVariable UUID orderId,
            @Valid @RequestBody ReassignOrderBranchRequest request) {
        FlowerChainPrincipal principal = branchGuard.requireGlobalHeadOfficeAccess();
        return orderService.reassignOrderBranch(
                orderId, request.newProcessingBranchId(), request.reason(), principal.actorId());
    }
}
