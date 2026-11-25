package com.flowerchain.order.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.flowerchain.audit.api.AuditApi;
import com.flowerchain.audit.api.AuditEntryCommand;
import com.flowerchain.order.api.OrderApi.OrderForPaymentDto;
import com.flowerchain.order.api.OrderApi.OrderStatusChangedEvent;
import com.flowerchain.order.api.OrderApi.VerifiedPaymentApplication;
import com.flowerchain.order.internal.OrderEntity;
import com.flowerchain.order.internal.OrderRepository;
import com.flowerchain.order.internal.OrderStatusHistoryEntity;
import com.flowerchain.order.internal.OrderStatusHistoryRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

/**
 * OrderService payment hooks used by the payment module. Only the mocks these two methods need are declared;
 * Mockito passes {@code null} for the other constructor arguments.
 */
@ExtendWith(MockitoExtension.class)
class OrderServiceVerifiedPaymentTest {

    private static final Instant NOW = Instant.parse("2026-11-25T08:00:00Z");
    private static final UUID PAYMENT_ID = UUID.fromString("00000000-0000-0000-0000-00000000d001");

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderStatusHistoryRepository statusHistoryRepository;

    @Mock
    private AuditApi auditApi;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private Clock clock;

    @InjectMocks
    private OrderService orderService;

    @Test
    void verifiedCaptureIsAppliedToAPendingUnpaidOrder() {
        OrderEntity order = order("PENDING_PAYMENT", "UNPAID");
        when(orderRepository.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));
        when(clock.instant()).thenReturn(NOW);

        VerifiedPaymentApplication result =
                orderService.markOrderPaidByVerifiedWebhook(order.getId(), PAYMENT_ID, "txn_9001");

        assertThat(result).isEqualTo(VerifiedPaymentApplication.APPLIED);
        assertThat(order.getStatus()).isEqualTo("CONFIRMED");
        assertThat(order.getPaymentStatus()).isEqualTo("PAID");
        verify(orderRepository).save(order);

        ArgumentCaptor<OrderStatusHistoryEntity> history = ArgumentCaptor.forClass(OrderStatusHistoryEntity.class);
        verify(statusHistoryRepository).save(history.capture());
        ArgumentCaptor<AuditEntryCommand> audit = ArgumentCaptor.forClass(AuditEntryCommand.class);
        verify(auditApi).record(audit.capture());
        assertThat(audit.getValue().action()).isEqualTo("ORDER_PAID_WEBHOOK");
        assertThat(audit.getValue().actorType()).isEqualTo("WEBHOOK");
        verify(eventPublisher).publishEvent(any(OrderStatusChangedEvent.class));
    }

    @Test
    void captureForCancelledOrderIsStaleAndWritesNothing() {
        OrderEntity order = order("CANCELLED", "UNPAID");
        when(orderRepository.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));
        when(clock.instant()).thenReturn(NOW);

        VerifiedPaymentApplication result =
                orderService.markOrderPaidByVerifiedWebhook(order.getId(), PAYMENT_ID, "txn_9001");

        assertThat(result).isEqualTo(VerifiedPaymentApplication.IGNORED_STALE);
        assertThat(order.getStatus()).isEqualTo("CANCELLED");
        assertThat(order.getPaymentStatus()).isEqualTo("UNPAID");
        verify(orderRepository, never()).save(any());
        verifyNoInteractions(statusHistoryRepository, auditApi, eventPublisher);
    }

    @Test
    void alreadyPaidOrderIsNotPaidTwice() {
        OrderEntity order = order("CONFIRMED", "PAID");
        when(orderRepository.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));
        when(clock.instant()).thenReturn(NOW);

        VerifiedPaymentApplication result =
                orderService.markOrderPaidByVerifiedWebhook(order.getId(), PAYMENT_ID, "txn_other");

        assertThat(result).isEqualTo(VerifiedPaymentApplication.IGNORED_STALE);
        verify(orderRepository, never()).save(any());
        verifyNoInteractions(statusHistoryRepository, auditApi, eventPublisher);
    }

    @Test
    void lockOrderForPaymentProcessingUsesTheRowLockAndMapsThePaymentView() {
        OrderEntity order = order("PENDING_PAYMENT", "UNPAID");
        when(orderRepository.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));

        Optional<OrderForPaymentDto> view = orderService.lockOrderForPaymentProcessing(order.getId());

        assertThat(view).hasValueSatisfying(dto -> {
            assertThat(dto.orderId()).isEqualTo(order.getId());
            assertThat(dto.processingBranchId()).isEqualTo(order.getProcessingBranchId());
            assertThat(dto.status()).isEqualTo("PENDING_PAYMENT");
            assertThat(dto.paymentStatus()).isEqualTo("UNPAID");
            assertThat(dto.totalAmount()).isEqualByComparingTo("500000.00");
            assertThat(dto.currencyCode()).isEqualTo("VND");
        });
    }

    @Test
    void lockOrderForPaymentProcessingReturnsEmptyForUnknownOrder() {
        UUID unknown = UUID.randomUUID();
        when(orderRepository.findByIdForUpdate(unknown)).thenReturn(Optional.empty());

        assertThat(orderService.lockOrderForPaymentProcessing(unknown)).isEmpty();
    }

    private static OrderEntity order(String status, String paymentStatus) {
        return new OrderEntity(
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
    }
}
