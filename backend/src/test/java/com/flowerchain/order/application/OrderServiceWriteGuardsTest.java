package com.flowerchain.order.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.flowerchain.audit.api.AuditApi;
import com.flowerchain.branch.api.BranchApi;
import com.flowerchain.inventory.api.InventoryApi;
import com.flowerchain.order.api.OrderApi.OrderDetailDto;
import com.flowerchain.order.internal.OrderEntity;
import com.flowerchain.order.internal.OrderLineRepository;
import com.flowerchain.order.internal.OrderRepository;
import com.flowerchain.order.internal.OrderStatusHistoryRepository;
import com.flowerchain.shared.api.FlowerChainException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;

/**
 * Security review F1 and F6: every order mutator takes the row lock (no lost updates against the payment
 * webhook), and staff cannot move an unpaid online-payment order out of PENDING_PAYMENT except to CANCELLED.
 */
@ExtendWith(MockitoExtension.class)
class OrderServiceWriteGuardsTest {

    private static final Instant NOW = Instant.parse("2026-11-25T08:00:00Z");
    private static final UUID STAFF_ID = UUID.fromString("00000000-0000-0000-0000-00000000e001");

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderLineRepository orderLineRepository;

    @Mock
    private OrderStatusHistoryRepository statusHistoryRepository;

    @Mock
    private BranchApi branchApi;

    @Mock
    private InventoryApi inventoryApi;

    @Mock
    private AuditApi auditApi;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private Clock clock;

    @InjectMocks
    private OrderService orderService;

    @BeforeEach
    void setUp() {
        lenient().when(clock.instant()).thenReturn(NOW);
        lenient().when(orderRepository.save(any(OrderEntity.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void customerCancelLocksTheOrderRow() {
        OrderEntity order = givenLockedOrder("PENDING_PAYMENT", "UNPAID");

        OrderDetailDto result = orderService.cancelCustomerOrder(order.getCustomerId(), order.getId(), "Changed my mind");

        assertThat(result.status()).isEqualTo("CANCELLED");
        verify(orderRepository).findByIdForUpdate(order.getId());
        verify(orderRepository, never()).findById(any());
    }

    @Test
    void customerCancelAfterACommittedCaptureKeepsThePaidStatus() {
        // With the row lock the cancel sees the committed capture, so it can never write UNPAID back.
        OrderEntity order = givenLockedOrder("CONFIRMED", "PAID");

        OrderDetailDto result = orderService.cancelCustomerOrder(order.getCustomerId(), order.getId(), "Changed my mind");

        assertThat(result.status()).isEqualTo("CANCELLED");
        assertThat(result.paymentStatus()).isEqualTo("PAID");
    }

    @Test
    void staffTransitionLocksTheOrderRow() {
        OrderEntity order = givenLockedOrder("CONFIRMED", "PAID");

        orderService.transitionOrderStatusByStaff(order.getId(), "PREPARING", null, STAFF_ID, true, Set.of());

        verify(orderRepository).findByIdForUpdate(order.getId());
        verify(orderRepository, never()).findById(any());
    }

    @Test
    void reassignLocksTheOrderRow() {
        OrderEntity order = givenLockedOrder("PENDING_PAYMENT", "UNPAID");

        orderService.reassignOrderBranch(order.getId(), UUID.randomUUID(), "Branch closed for the day", STAFF_ID);

        verify(orderRepository).findByIdForUpdate(order.getId());
        verify(orderRepository, never()).findById(any());
    }

    @Test
    void reassignAfterACommittedCaptureKeepsTheOrderPaid() {
        // Lost-update regression: the reassign must never put a paid order back to PENDING_PAYMENT/UNPAID.
        OrderEntity order = givenLockedOrder("CONFIRMED", "PAID");

        OrderDetailDto result =
                orderService.reassignOrderBranch(order.getId(), UUID.randomUUID(), "Branch closed for the day", STAFF_ID);

        assertThat(result.status()).isEqualTo("CONFIRMED");
        assertThat(result.paymentStatus()).isEqualTo("PAID");
    }

    @Test
    void recordOrderRefundedLocksTheOrderRow() {
        OrderEntity order = givenLockedOrder("CONFIRMED", "PAID");

        orderService.recordOrderRefunded(order.getId(), new BigDecimal("500000.00"), true, STAFF_ID, "Refund");

        verify(orderRepository).findByIdForUpdate(order.getId());
        verify(orderRepository, never()).findById(any());
        assertThat(order.getPaymentStatus()).isEqualTo("REFUNDED");
    }

    @ParameterizedTest
    @ValueSource(strings = {"CONFIRMED", "PREPARING", "READY_FOR_PICKUP", "COMPLETED"})
    void staffCannotMoveAnUnpaidOnlineOrderOutOfPendingPaymentExceptToCancel(String toStatus) {
        OrderEntity order = givenLockedOrder("PENDING_PAYMENT", "UNPAID");

        assertThatThrownBy(() -> orderService.transitionOrderStatusByStaff(
                        order.getId(), toStatus, null, STAFF_ID, true, Set.of()))
                .isInstanceOfSatisfying(FlowerChainException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(e.getErrorCode()).isEqualTo("INVALID_ORDER_TRANSITION");
                });

        assertThat(order.getStatus()).isEqualTo("PENDING_PAYMENT");
        verify(orderRepository, never()).save(any());
        verifyNoInteractions(inventoryApi, statusHistoryRepository, auditApi, eventPublisher);
    }

    @Test
    void staffMayCancelAPendingPaymentOrder() {
        OrderEntity order = givenLockedOrder("PENDING_PAYMENT", "UNPAID");

        OrderDetailDto result = orderService.transitionOrderStatusByStaff(
                order.getId(), "CANCELLED", "Customer asked by phone", STAFF_ID, true, Set.of());

        assertThat(result.status()).isEqualTo("CANCELLED");
        verify(inventoryApi).releaseOrderStock(
                eq(order.getProcessingBranchId()), eq(order.getId()), anyMap(), eq(STAFF_ID), eq("STAFF"), anyString());
    }

    @Test
    void confirmedOrdersStillMoveForwardAsBefore() {
        // COD / PAY_AT_PICKUP orders start CONFIRMED and are unaffected by the PENDING_PAYMENT rule.
        OrderEntity order = givenLockedOrder("CONFIRMED", "UNPAID");

        OrderDetailDto result =
                orderService.transitionOrderStatusByStaff(order.getId(), "PREPARING", null, STAFF_ID, true, Set.of());

        assertThat(result.status()).isEqualTo("PREPARING");
    }

    private OrderEntity givenLockedOrder(String status, String paymentStatus) {
        OrderEntity order = new OrderEntity(
                UUID.randomUUID(),
                "ORD-2001",
                UUID.randomUUID(),
                UUID.randomUUID(),
                "PICKUP",
                status,
                paymentStatus,
                "VND",
                new BigDecimal("500000.00"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                new BigDecimal("500000.00"),
                "Recipient",
                "0900000000",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                Instant.parse("2026-11-25T07:00:00Z"));
        when(orderRepository.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));
        return order;
    }
}
