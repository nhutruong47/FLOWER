package com.flowerchain.payment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.flowerchain.audit.api.AuditApi;
import com.flowerchain.audit.api.AuditEntryCommand;
import com.flowerchain.order.api.OrderApi;
import com.flowerchain.order.api.OrderApi.OrderForPaymentDto;
import com.flowerchain.order.api.OrderApi.VerifiedPaymentApplication;
import com.flowerchain.payment.gateway.VerifiedPaymentEvent;
import com.flowerchain.payment.internal.PaymentEntity;
import com.flowerchain.payment.internal.PaymentRepository;
import com.flowerchain.payment.internal.PaymentWebhookEventEntity;
import com.flowerchain.payment.internal.PaymentWebhookEventRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

/** FR-026 / REQ-010 decision logic. TEST-012 at unit level; stale and mismatch handling. */
@ExtendWith(MockitoExtension.class)
class PaymentWebhookEventProcessorTest {

    private static final String PROVIDER = "sandbox-hmac";
    private static final Instant NOW = Instant.parse("2026-11-25T08:00:00Z");
    private static final Instant OCCURRED_AT = Instant.parse("2026-11-25T07:59:00Z");
    private static final UUID ORDER_ID = UUID.fromString("00000000-0000-0000-0000-00000000a001");
    private static final UUID OTHER_ORDER_ID = UUID.fromString("00000000-0000-0000-0000-00000000a002");
    private static final UUID BRANCH_ID = UUID.fromString("00000000-0000-0000-0000-00000000b001");
    private static final UUID CUSTOMER_ID = UUID.fromString("00000000-0000-0000-0000-00000000c001");
    private static final BigDecimal ORDER_TOTAL = new BigDecimal("500000.00");

    @Mock
    private OrderApi orderApi;

    @Mock
    private AuditApi auditApi;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PaymentWebhookEventRepository eventRepository;

    private PaymentWebhookEventProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new PaymentWebhookEventProcessor(
                orderApi,
                auditApi,
                paymentRepository,
                eventRepository,
                JsonMapper.builder().build(),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void firstCapturedEventMarksOrderPaidAndRecordsPaymentAndEvent() {
        givenLockedOrder(order("PENDING_PAYMENT", "UNPAID"));
        givenNewEvent("evt_1001");
        when(paymentRepository.findByProviderAndProviderTransactionId(PROVIDER, "txn_9001")).thenReturn(Optional.empty());
        when(orderApi.markOrderPaidByVerifiedWebhook(eq(ORDER_ID), any(UUID.class), eq("txn_9001")))
                .thenReturn(VerifiedPaymentApplication.APPLIED);

        // "500000" vs order total "500000.00": equal by BigDecimal.compareTo.
        WebhookOutcome outcome = processor.process(event("evt_1001", "txn_9001", "CAPTURED", "500000", "VND"));

        assertThat(outcome).isEqualTo(WebhookOutcome.PROCESSED);
        PaymentEntity payment = savedPayment();
        assertThat(payment.getStatus()).isEqualTo("CAPTURED");
        assertThat(payment.getOrderId()).isEqualTo(ORDER_ID);
        assertThat(payment.getProvider()).isEqualTo(PROVIDER);
        assertThat(payment.getProviderTransactionId()).isEqualTo("txn_9001");
        assertThat(payment.getPaymentMethod()).isEqualTo("ONLINE_GATEWAY");
        assertThat(payment.getAmount()).isEqualByComparingTo(ORDER_TOTAL);
        assertThat(payment.getCurrencyCode()).isEqualTo("VND");
        assertThat(payment.getCapturedAt()).isEqualTo(OCCURRED_AT);
        verify(orderApi).markOrderPaidByVerifiedWebhook(ORDER_ID, payment.getId(), "txn_9001");

        PaymentWebhookEventEntity recorded = savedEvent();
        assertThat(recorded.getProvider()).isEqualTo(PROVIDER);
        assertThat(recorded.getProviderEventId()).isEqualTo("evt_1001");
        assertThat(recorded.getProviderTransactionId()).isEqualTo("txn_9001");
        assertThat(recorded.getOrderId()).isEqualTo(ORDER_ID);
        assertThat(recorded.isSignatureVerified()).isTrue();
        assertThat(recorded.getProcessingOutcome()).isEqualTo("PROCESSED");
        // The order module writes the ORDER_PAID_WEBHOOK audit entry for an applied capture.
        verifyNoInteractions(auditApi);
    }

    @Test
    void duplicateEventIdIsAcknowledgedWithoutAnySideEffect() {
        givenLockedOrder(order("CONFIRMED", "PAID"));
        when(eventRepository.existsByProviderAndProviderEventId(PROVIDER, "evt_1001")).thenReturn(true);

        WebhookOutcome outcome = processor.process(event("evt_1001", "txn_9001", "CAPTURED", "500000", "VND"));

        assertThat(outcome).isEqualTo(WebhookOutcome.ALREADY_PROCESSED_IDEMPOTENT);
        verify(eventRepository, never()).saveAndFlush(any());
        verifyNoInteractions(paymentRepository, auditApi);
        verify(orderApi, never()).markOrderPaidByVerifiedWebhook(any(), any(), anyString());
    }

    @Test
    void amountMismatchNeverMarksTheOrderPaidAndIsRecordedAndAudited() {
        givenLockedOrder(order("PENDING_PAYMENT", "UNPAID"));
        givenNewEvent("evt_1001");
        when(paymentRepository.findByProviderAndProviderTransactionId(PROVIDER, "txn_9001")).thenReturn(Optional.empty());

        WebhookOutcome outcome = processor.process(event("evt_1001", "txn_9001", "CAPTURED", "499999.99", "VND"));

        assertThat(outcome).isEqualTo(WebhookOutcome.REJECTED_AMOUNT_MISMATCH);
        verify(orderApi, never()).markOrderPaidByVerifiedWebhook(any(), any(), anyString());
        verify(paymentRepository, never()).saveAndFlush(any());
        assertThat(savedEvent().getProcessingOutcome()).isEqualTo("REJECTED_AMOUNT_MISMATCH");
        AuditEntryCommand audit = recordedAudit();
        assertThat(audit.action()).isEqualTo("PAYMENT_WEBHOOK_AMOUNT_MISMATCH");
        assertThat(audit.category()).isEqualTo("ORDER_STATUS");
        assertThat(audit.actorType()).isEqualTo("WEBHOOK");
        assertThat(audit.branchId()).isEqualTo(BRANCH_ID);
        assertThat(audit.targetEntityType()).isEqualTo("ORDER");
        assertThat(audit.targetEntityId()).isEqualTo(ORDER_ID);
        assertThat(audit.newValuesJson())
                .contains("\"amount\":\"499999.99\"")
                .contains("\"orderTotalAmount\":\"500000.00\"")
                .contains("\"requiresManualReconciliation\":true");
    }

    @Test
    void currencyMismatchIsRejectedLikeAnAmountMismatch() {
        givenLockedOrder(order("PENDING_PAYMENT", "UNPAID"));
        givenNewEvent("evt_1001");
        when(paymentRepository.findByProviderAndProviderTransactionId(PROVIDER, "txn_9001")).thenReturn(Optional.empty());

        WebhookOutcome outcome = processor.process(event("evt_1001", "txn_9001", "CAPTURED", "500000.00", "USD"));

        assertThat(outcome).isEqualTo(WebhookOutcome.REJECTED_AMOUNT_MISMATCH);
        verify(orderApi, never()).markOrderPaidByVerifiedWebhook(any(), any(), anyString());
        verify(paymentRepository, never()).saveAndFlush(any());
    }

    @Test
    void captureForCancelledOrderIsIgnoredAsStaleAndFlaggedForReconciliation() {
        givenLockedOrder(order("CANCELLED", "UNPAID"));
        givenNewEvent("evt_1001");
        when(paymentRepository.findByProviderAndProviderTransactionId(PROVIDER, "txn_9001")).thenReturn(Optional.empty());

        WebhookOutcome outcome = processor.process(event("evt_1001", "txn_9001", "CAPTURED", "500000", "VND"));

        assertThat(outcome).isEqualTo(WebhookOutcome.IGNORED_STALE);
        verify(orderApi, never()).markOrderPaidByVerifiedWebhook(any(), any(), anyString());
        verify(paymentRepository, never()).saveAndFlush(any());
        assertThat(savedEvent().getProcessingOutcome()).isEqualTo("IGNORED_STALE");
        AuditEntryCommand audit = recordedAudit();
        assertThat(audit.action()).isEqualTo("PAYMENT_WEBHOOK_IGNORED_STALE");
        assertThat(audit.newValuesJson())
                .contains("\"orderStatus\":\"CANCELLED\"")
                .contains("\"requiresManualReconciliation\":true");
    }

    @Test
    void failedAfterCapturedForTheSameTransactionIsIgnoredAsStale() {
        givenLockedOrder(order("CONFIRMED", "PAID"));
        givenNewEvent("evt_1002");
        PaymentEntity captured = payment(ORDER_ID, "txn_9001", "CAPTURED");
        when(paymentRepository.findByProviderAndProviderTransactionId(PROVIDER, "txn_9001"))
                .thenReturn(Optional.of(captured));

        WebhookOutcome outcome = processor.process(event("evt_1002", "txn_9001", "FAILED", "500000", "VND"));

        assertThat(outcome).isEqualTo(WebhookOutcome.IGNORED_STALE);
        assertThat(captured.getStatus()).isEqualTo("CAPTURED");
        verify(paymentRepository, never()).saveAndFlush(any());
        verify(orderApi, never()).markOrderPaidByVerifiedWebhook(any(), any(), anyString());
        assertThat(savedEvent().getProcessingOutcome()).isEqualTo("IGNORED_STALE");
        AuditEntryCommand audit = recordedAudit();
        assertThat(audit.action()).isEqualTo("PAYMENT_WEBHOOK_IGNORED_STALE");
        assertThat(audit.newValuesJson()).contains("\"requiresManualReconciliation\":false");
    }

    @Test
    void failedAfterTheOrderWasPaidByAnotherTransactionIsIgnoredAsStale() {
        givenLockedOrder(order("CONFIRMED", "PAID"));
        givenNewEvent("evt_2001");
        when(paymentRepository.findByProviderAndProviderTransactionId(PROVIDER, "txn_other")).thenReturn(Optional.empty());

        WebhookOutcome outcome = processor.process(event("evt_2001", "txn_other", "FAILED", "500000", "VND"));

        assertThat(outcome).isEqualTo(WebhookOutcome.IGNORED_STALE);
        verify(paymentRepository, never()).saveAndFlush(any());
        verify(orderApi, never()).markOrderPaidByVerifiedWebhook(any(), any(), anyString());
    }

    @Test
    void secondCaptureForAnAlreadyPaidOrderIsIgnoredAsStale() {
        givenLockedOrder(order("CONFIRMED", "PAID"));
        givenNewEvent("evt_3001");
        when(paymentRepository.findByProviderAndProviderTransactionId(PROVIDER, "txn_second")).thenReturn(Optional.empty());

        WebhookOutcome outcome = processor.process(event("evt_3001", "txn_second", "CAPTURED", "500000", "VND"));

        assertThat(outcome).isEqualTo(WebhookOutcome.IGNORED_STALE);
        verify(paymentRepository, never()).saveAndFlush(any());
        verify(orderApi, never()).markOrderPaidByVerifiedWebhook(any(), any(), anyString());
        assertThat(recordedAudit().newValuesJson()).contains("\"requiresManualReconciliation\":true");
    }

    @Test
    void failedEventForPendingOrderRecordsFailedPaymentAndLeavesOrderUnpaid() {
        givenLockedOrder(order("PENDING_PAYMENT", "UNPAID"));
        givenNewEvent("evt_4001");
        when(paymentRepository.findByProviderAndProviderTransactionId(PROVIDER, "txn_4001")).thenReturn(Optional.empty());

        WebhookOutcome outcome = processor.process(event("evt_4001", "txn_4001", "FAILED", "500000", "VND"));

        assertThat(outcome).isEqualTo(WebhookOutcome.PROCESSED);
        PaymentEntity payment = savedPayment();
        assertThat(payment.getStatus()).isEqualTo("FAILED");
        assertThat(payment.getCapturedAt()).isNull();
        verify(orderApi, never()).markOrderPaidByVerifiedWebhook(any(), any(), anyString());
        assertThat(recordedAudit().action()).isEqualTo("PAYMENT_FAILED_WEBHOOK");
    }

    @Test
    void newEventIdForAnAlreadyCapturedTransactionIsIdempotent() {
        givenLockedOrder(order("CONFIRMED", "PAID"));
        givenNewEvent("evt_1001_resent");
        when(paymentRepository.findByProviderAndProviderTransactionId(PROVIDER, "txn_9001"))
                .thenReturn(Optional.of(payment(ORDER_ID, "txn_9001", "CAPTURED")));

        WebhookOutcome outcome = processor.process(event("evt_1001_resent", "txn_9001", "CAPTURED", "500000", "VND"));

        assertThat(outcome).isEqualTo(WebhookOutcome.ALREADY_PROCESSED_IDEMPOTENT);
        assertThat(savedEvent().getProcessingOutcome()).isEqualTo("ALREADY_PROCESSED_IDEMPOTENT");
        verify(paymentRepository, never()).saveAndFlush(any());
        verify(orderApi, never()).markOrderPaidByVerifiedWebhook(any(), any(), anyString());
        verifyNoInteractions(auditApi);
    }

    @Test
    void captureAfterAnEarlierFailedReportForTheSameTransactionIsApplied() {
        givenLockedOrder(order("PENDING_PAYMENT", "UNPAID"));
        givenNewEvent("evt_5002");
        PaymentEntity failed = payment(ORDER_ID, "txn_5000", "FAILED");
        when(paymentRepository.findByProviderAndProviderTransactionId(PROVIDER, "txn_5000"))
                .thenReturn(Optional.of(failed));
        when(orderApi.markOrderPaidByVerifiedWebhook(ORDER_ID, failed.getId(), "txn_5000"))
                .thenReturn(VerifiedPaymentApplication.APPLIED);

        WebhookOutcome outcome = processor.process(event("evt_5002", "txn_5000", "CAPTURED", "500000", "VND"));

        assertThat(outcome).isEqualTo(WebhookOutcome.PROCESSED);
        assertThat(savedPayment()).isSameAs(failed);
        assertThat(failed.getStatus()).isEqualTo("CAPTURED");
        assertThat(failed.getCapturedAt()).isEqualTo(OCCURRED_AT);
    }

    @Test
    void unknownOrderIsRecordedAndAuditedWithoutTouchingPayments() {
        when(orderApi.lockOrderForPaymentProcessing(ORDER_ID)).thenReturn(Optional.empty());
        givenNewEvent("evt_6001");

        WebhookOutcome outcome = processor.process(event("evt_6001", "txn_6001", "CAPTURED", "500000", "VND"));

        assertThat(outcome).isEqualTo(WebhookOutcome.REJECTED_UNKNOWN_ORDER);
        PaymentWebhookEventEntity recorded = savedEvent();
        assertThat(recorded.getOrderId()).isNull();
        assertThat(recorded.getProcessingOutcome()).isEqualTo("REJECTED_UNKNOWN_ORDER");
        verifyNoInteractions(paymentRepository);
        AuditEntryCommand audit = recordedAudit();
        assertThat(audit.action()).isEqualTo("PAYMENT_WEBHOOK_UNKNOWN_ORDER");
        assertThat(audit.branchId()).isNull();
        assertThat(audit.targetEntityType()).isEqualTo("PAYMENT_WEBHOOK_EVENT");
        assertThat(audit.targetEntityId()).isEqualTo(recorded.getId());
    }

    @Test
    void transactionAlreadyRecordedForAnotherOrderIsRejected() {
        givenLockedOrder(order("PENDING_PAYMENT", "UNPAID"));
        givenNewEvent("evt_7001");
        when(paymentRepository.findByProviderAndProviderTransactionId(PROVIDER, "txn_7000"))
                .thenReturn(Optional.of(payment(OTHER_ORDER_ID, "txn_7000", "CAPTURED")));

        WebhookOutcome outcome = processor.process(event("evt_7001", "txn_7000", "CAPTURED", "500000", "VND"));

        assertThat(outcome).isEqualTo(WebhookOutcome.REJECTED_ORDER_MISMATCH);
        verify(paymentRepository, never()).saveAndFlush(any());
        verify(orderApi, never()).markOrderPaidByVerifiedWebhook(any(), any(), anyString());
        assertThat(recordedAudit().action()).isEqualTo("PAYMENT_WEBHOOK_ORDER_MISMATCH");
    }

    @Test
    void captureIsIgnoredWhenTheOrderModuleReportsTheOrderAsStale() {
        givenLockedOrder(order("PENDING_PAYMENT", "UNPAID"));
        givenNewEvent("evt_8001");
        when(paymentRepository.findByProviderAndProviderTransactionId(PROVIDER, "txn_8001")).thenReturn(Optional.empty());
        when(orderApi.markOrderPaidByVerifiedWebhook(eq(ORDER_ID), any(UUID.class), eq("txn_8001")))
                .thenReturn(VerifiedPaymentApplication.IGNORED_STALE);

        WebhookOutcome outcome = processor.process(event("evt_8001", "txn_8001", "CAPTURED", "500000", "VND"));

        assertThat(outcome).isEqualTo(WebhookOutcome.IGNORED_STALE);
        verify(paymentRepository, never()).saveAndFlush(any());
        assertThat(savedEvent().getProcessingOutcome()).isEqualTo("IGNORED_STALE");
    }

    @Test
    void isEventRecordedDelegatesToTheUniqueKeyLookup() {
        when(eventRepository.existsByProviderAndProviderEventId(PROVIDER, "evt_1001")).thenReturn(true);
        assertThat(processor.isEventRecorded(PROVIDER, "evt_1001")).isTrue();
    }

    private void givenLockedOrder(OrderForPaymentDto order) {
        when(orderApi.lockOrderForPaymentProcessing(ORDER_ID)).thenReturn(Optional.of(order));
    }

    private void givenNewEvent(String eventId) {
        when(eventRepository.existsByProviderAndProviderEventId(PROVIDER, eventId)).thenReturn(false);
    }

    private PaymentEntity savedPayment() {
        ArgumentCaptor<PaymentEntity> captor = ArgumentCaptor.forClass(PaymentEntity.class);
        verify(paymentRepository).saveAndFlush(captor.capture());
        return captor.getValue();
    }

    private PaymentWebhookEventEntity savedEvent() {
        ArgumentCaptor<PaymentWebhookEventEntity> captor = ArgumentCaptor.forClass(PaymentWebhookEventEntity.class);
        verify(eventRepository).saveAndFlush(captor.capture());
        return captor.getValue();
    }

    private AuditEntryCommand recordedAudit() {
        ArgumentCaptor<AuditEntryCommand> captor = ArgumentCaptor.forClass(AuditEntryCommand.class);
        verify(auditApi).record(captor.capture());
        return captor.getValue();
    }

    private static OrderForPaymentDto order(String status, String paymentStatus) {
        return new OrderForPaymentDto(
                ORDER_ID, "ORD-2001", CUSTOMER_ID, BRANCH_ID, status, paymentStatus, ORDER_TOTAL, "VND");
    }

    private static PaymentEntity payment(UUID orderId, String transactionId, String status) {
        return new PaymentEntity(
                UUID.randomUUID(),
                orderId,
                PROVIDER,
                "ONLINE_GATEWAY",
                transactionId,
                status,
                ORDER_TOTAL,
                "VND",
                "CAPTURED".equals(status) ? OCCURRED_AT : null,
                NOW);
    }

    private static VerifiedPaymentEvent event(
            String eventId, String transactionId, String status, String amount, String currencyCode) {
        return new VerifiedPaymentEvent(
                PROVIDER,
                eventId,
                transactionId,
                ORDER_ID,
                VerifiedPaymentEvent.Status.valueOf(status),
                new BigDecimal(amount),
                currencyCode,
                OCCURRED_AT);
    }
}
