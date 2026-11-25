package com.flowerchain.payment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.flowerchain.audit.api.AuditApi;
import com.flowerchain.audit.api.AuditEntryCommand;
import com.flowerchain.order.api.OrderApi;
import com.flowerchain.order.api.OrderApi.OrderForPaymentDto;
import com.flowerchain.payment.internal.PaymentEntity;
import com.flowerchain.payment.internal.PaymentRefundEntity;
import com.flowerchain.payment.internal.PaymentRefundRepository;
import com.flowerchain.payment.internal.PaymentRepository;
import com.flowerchain.shared.api.FlowerChainException;
import com.flowerchain.shared.api.FlowerChainPrincipal;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.json.JsonMapper;

/** FR-027 refunds: limit, full refund, audit, and branch scope (REQ-011, REQ-012). */
@ExtendWith(MockitoExtension.class)
class PaymentRefundServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-25T08:00:00Z");
    private static final UUID PAYMENT_ID = UUID.fromString("00000000-0000-0000-0000-00000000d001");
    private static final UUID ORDER_ID = UUID.fromString("00000000-0000-0000-0000-00000000a001");
    private static final UUID BRANCH_A = UUID.fromString("00000000-0000-0000-0000-00000000b00a");
    private static final UUID BRANCH_B = UUID.fromString("00000000-0000-0000-0000-00000000b00b");
    private static final BigDecimal CAPTURED = new BigDecimal("500000.00");
    private static final String REASON = "Customer cancelled before preparation";

    private static final FlowerChainPrincipal HEAD_OFFICE =
            FlowerChainPrincipal.staff(UUID.randomUUID(), "ho@example.test", Set.of("HEAD_OFFICE_ADMIN"), Set.of());
    private static final FlowerChainPrincipal OWNER =
            FlowerChainPrincipal.staff(UUID.randomUUID(), "owner@example.test", Set.of("SYSTEM_OWNER"), Set.of());
    private static final FlowerChainPrincipal MANAGER_A =
            FlowerChainPrincipal.staff(UUID.randomUUID(), "ma@example.test", Set.of("BRANCH_MANAGER"), Set.of(BRANCH_A));
    private static final FlowerChainPrincipal MANAGER_B =
            FlowerChainPrincipal.staff(UUID.randomUUID(), "mb@example.test", Set.of("BRANCH_MANAGER"), Set.of(BRANCH_B));
    private static final FlowerChainPrincipal SALES_A =
            FlowerChainPrincipal.staff(UUID.randomUUID(), "sa@example.test", Set.of("SALES_STAFF"), Set.of(BRANCH_A));

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PaymentRefundRepository refundRepository;

    @Mock
    private OrderApi orderApi;

    @Mock
    private AuditApi auditApi;

    private PaymentRefundService service;

    @BeforeEach
    void setUp() {
        service = new PaymentRefundService(
                paymentRepository,
                refundRepository,
                orderApi,
                auditApi,
                JsonMapper.builder().build(),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void fullRefundMarksPaymentRefundedUpdatesOrderAndWritesRefundAudit() {
        PaymentEntity payment = givenPayment("CAPTURED", null);
        givenRefundSaved();

        RefundResponse response =
                service.createRefund(HEAD_OFFICE, PAYMENT_ID, new BigDecimal("500000"), "  " + REASON + "  ", "BANK-REF-42");

        assertThat(payment.getStatus()).isEqualTo("REFUNDED");
        verify(paymentRepository).save(payment);
        verify(orderApi).recordOrderRefunded(ORDER_ID, new BigDecimal("500000.00"), true, HEAD_OFFICE.actorId(), REASON);

        assertThat(response.paymentId()).isEqualTo(PAYMENT_ID);
        assertThat(response.orderId()).isEqualTo(ORDER_ID);
        assertThat(response.refundAmount()).isEqualTo("500000.00");
        assertThat(response.reason()).isEqualTo(REASON);
        assertThat(response.externalRefundRef()).isEqualTo("BANK-REF-42");
        assertThat(response.approvedByStaffId()).isEqualTo(HEAD_OFFICE.actorId());
        assertThat(response.refundedAt()).isEqualTo(NOW);

        AuditEntryCommand audit = recordedAudit();
        assertThat(audit.category()).isEqualTo("REFUND");
        assertThat(audit.action()).isEqualTo("PAYMENT_REFUND_RECORDED");
        assertThat(audit.branchId()).isEqualTo(BRANCH_A);
        assertThat(audit.targetEntityType()).isEqualTo("PAYMENT_REFUND");
        assertThat(audit.targetEntityId()).isEqualTo(response.id());
        assertThat(audit.actorId()).isEqualTo(HEAD_OFFICE.actorId());
        assertThat(audit.actorType()).isEqualTo("STAFF");
        assertThat(audit.oldValuesJson()).contains("\"paymentStatus\":\"CAPTURED\"");
        assertThat(audit.newValuesJson())
                .contains("\"paymentStatus\":\"REFUNDED\"")
                .contains("\"refundAmount\":\"500000.00\"")
                .contains("\"reason\":\"" + REASON + "\"")
                .contains("\"externalRefundRef\":\"BANK-REF-42\"")
                .contains("\"paymentId\":\"" + PAYMENT_ID + "\"");
    }

    @Test
    void partialRefundKeepsPaymentCaptured() {
        PaymentEntity payment = givenPayment("CAPTURED", null);
        givenRefundSaved();

        RefundResponse response = service.createRefund(OWNER, PAYMENT_ID, new BigDecimal("200000"), REASON, null);

        assertThat(payment.getStatus()).isEqualTo("CAPTURED");
        verify(paymentRepository, never()).save(any());
        verify(orderApi).recordOrderRefunded(ORDER_ID, new BigDecimal("200000.00"), false, OWNER.actorId(), REASON);
        assertThat(response.externalRefundRef()).isNull();
    }

    @Test
    void refundThatCompletesEarlierPartialRefundsIsAFullRefund() {
        PaymentEntity payment = givenPayment("CAPTURED", new BigDecimal("300000.00"));
        givenRefundSaved();

        service.createRefund(HEAD_OFFICE, PAYMENT_ID, new BigDecimal("200000.00"), REASON, null);

        assertThat(payment.getStatus()).isEqualTo("REFUNDED");
        verify(orderApi).recordOrderRefunded(ORDER_ID, new BigDecimal("200000.00"), true, HEAD_OFFICE.actorId(), REASON);
    }

    @Test
    void refundAboveTheRemainingCapturedAmountIsRejected() {
        givenPayment("CAPTURED", new BigDecimal("300000.00"));

        assertProblem(
                () -> service.createRefund(HEAD_OFFICE, PAYMENT_ID, new BigDecimal("200000.01"), REASON, null),
                HttpStatus.CONFLICT,
                "REFUND_EXCEEDS_CAPTURED_AMOUNT");
        assertNothingWritten();
    }

    @Test
    void refundAboveTheCapturedAmountIsRejected() {
        givenPayment("CAPTURED", null);

        assertProblem(
                () -> service.createRefund(HEAD_OFFICE, PAYMENT_ID, new BigDecimal("500000.01"), REASON, null),
                HttpStatus.CONFLICT,
                "REFUND_EXCEEDS_CAPTURED_AMOUNT");
        assertNothingWritten();
    }

    @Test
    void branchManagersAreForbiddenUntilB20IsAnswered() {
        // F5: roles-and-permissions.md marks BRANCH_MANAGER refunds "BRANCH ?" pending B20.
        assertThat(PaymentRefundService.REFUND_ROLES).containsExactlyInAnyOrder("SYSTEM_OWNER", "HEAD_OFFICE_ADMIN");
        assertProblem(
                () -> service.createRefund(MANAGER_A, PAYMENT_ID, new BigDecimal("1000"), REASON, null),
                HttpStatus.FORBIDDEN,
                "AUTH_FORBIDDEN_ROLE");
        assertProblem(
                () -> service.createRefund(MANAGER_B, PAYMENT_ID, new BigDecimal("1000"), REASON, null),
                HttpStatus.FORBIDDEN,
                "AUTH_FORBIDDEN_ROLE");
        verifyNoInteractions(paymentRepository, refundRepository, orderApi, auditApi);
    }

    @Test
    void callerWithoutAccessToTheProcessingBranchGets404AndNothingIsWritten() {
        // Defence in depth (REQ-011/012): a refund role without access to the order's branch must not see it.
        FlowerChainPrincipal scopedAdmin = new FlowerChainPrincipal(
                UUID.randomUUID(), "scoped@example.test", "STAFF", Set.of("HEAD_OFFICE_ADMIN"), false, Set.of(BRANCH_B));
        givenPaymentWithoutRefundLookup("CAPTURED");

        assertProblem(
                () -> service.createRefund(scopedAdmin, PAYMENT_ID, new BigDecimal("1000"), REASON, null),
                HttpStatus.NOT_FOUND,
                "RESOURCE_NOT_FOUND");
        assertNothingWritten();
    }

    @Test
    void rolesWithoutRefundPermissionAreForbiddenBeforeAnyLookup() {
        FlowerChainPrincipal customer = FlowerChainPrincipal.customer(UUID.randomUUID(), "c@example.test");

        assertProblem(
                () -> service.createRefund(SALES_A, PAYMENT_ID, new BigDecimal("1000"), REASON, null),
                HttpStatus.FORBIDDEN,
                "AUTH_FORBIDDEN_ROLE");
        assertProblem(
                () -> service.createRefund(customer, PAYMENT_ID, new BigDecimal("1000"), REASON, null),
                HttpStatus.FORBIDDEN,
                "AUTH_FORBIDDEN_ROLE");
        verifyNoInteractions(paymentRepository, refundRepository, orderApi, auditApi);
    }

    @Test
    void unknownPaymentGets404() {
        when(paymentRepository.findByIdForUpdate(PAYMENT_ID)).thenReturn(Optional.empty());

        assertProblem(
                () -> service.createRefund(HEAD_OFFICE, PAYMENT_ID, new BigDecimal("1000"), REASON, null),
                HttpStatus.NOT_FOUND,
                "RESOURCE_NOT_FOUND");
        assertNothingWritten();
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "0.00", "-1", "1.001"})
    void nonPositiveOrOverPreciseAmountIsRejected(String amount) {
        assertProblem(
                () -> service.createRefund(HEAD_OFFICE, PAYMENT_ID, new BigDecimal(amount), REASON, null),
                HttpStatus.BAD_REQUEST,
                "REFUND_AMOUNT_INVALID");
        verifyNoInteractions(paymentRepository, refundRepository, orderApi, auditApi);
    }

    @Test
    void failedPaymentCannotBeRefunded() {
        givenPaymentWithoutRefundLookup("FAILED");

        assertProblem(
                () -> service.createRefund(HEAD_OFFICE, PAYMENT_ID, new BigDecimal("1000"), REASON, null),
                HttpStatus.CONFLICT,
                "PAYMENT_NOT_CAPTURED");
        assertNothingWritten();
    }

    @Test
    void fullyRefundedPaymentCannotBeRefundedAgain() {
        givenPaymentWithoutRefundLookup("REFUNDED");

        assertProblem(
                () -> service.createRefund(HEAD_OFFICE, PAYMENT_ID, new BigDecimal("1000"), REASON, null),
                HttpStatus.CONFLICT,
                "PAYMENT_ALREADY_FULLY_REFUNDED");
        assertNothingWritten();
    }

    private PaymentEntity givenPayment(String status, BigDecimal alreadyRefunded) {
        PaymentEntity payment = givenPaymentWithoutRefundLookup(status);
        when(refundRepository.sumRefundAmountByPaymentId(PAYMENT_ID)).thenReturn(alreadyRefunded);
        return payment;
    }

    private PaymentEntity givenPaymentWithoutRefundLookup(String status) {
        PaymentEntity payment = new PaymentEntity(
                PAYMENT_ID, ORDER_ID, "sandbox-hmac", "ONLINE_GATEWAY", "txn_9001", status, CAPTURED, "VND", NOW, NOW);
        when(paymentRepository.findByIdForUpdate(PAYMENT_ID)).thenReturn(Optional.of(payment));
        when(orderApi.getOrderForPaymentOrThrow(ORDER_ID)).thenReturn(new OrderForPaymentDto(
                ORDER_ID, "ORD-2001", UUID.randomUUID(), BRANCH_A, "CONFIRMED", "PAID", CAPTURED, "VND"));
        return payment;
    }

    private void givenRefundSaved() {
        when(refundRepository.saveAndFlush(any(PaymentRefundEntity.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private AuditEntryCommand recordedAudit() {
        ArgumentCaptor<AuditEntryCommand> captor = ArgumentCaptor.forClass(AuditEntryCommand.class);
        verify(auditApi).record(captor.capture());
        return captor.getValue();
    }

    private void assertNothingWritten() {
        verify(refundRepository, never()).saveAndFlush(any());
        verify(paymentRepository, never()).save(any());
        verify(orderApi, never()).recordOrderRefunded(any(), any(), anyBoolean(), any(), anyString());
        verifyNoInteractions(auditApi);
    }

    private static void assertProblem(Runnable call, HttpStatus status, String errorCode) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(FlowerChainException.class, e -> {
            assertThat(e.getStatus()).isEqualTo(status);
            assertThat(e.getErrorCode()).isEqualTo(errorCode);
        });
    }
}
