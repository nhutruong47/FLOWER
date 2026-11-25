package com.flowerchain.payment.application;

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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * Records refunds of captured payments (FR-027, BRULE-025). The money movement at the provider is manual and
 * out of scope; staff record it here with {@code externalRefundRef}.
 *
 * <p>Authorization (roles-and-permissions.md, FR-027 row): {@code SYSTEM_OWNER} and {@code HEAD_OFFICE_ADMIN}
 * for every branch; {@code BRANCH_MANAGER} only for payments whose order is processed by one of the manager's
 * server-resolved branches (REQ-011, REQ-012). Other branches get 404 so the payment's existence does not
 * leak.
 */
@Service
public class PaymentRefundService {

    /** Roles that may record a refund (branch scope is checked separately). */
    public static final List<String> REFUND_ROLES = List.of("SYSTEM_OWNER", "HEAD_OFFICE_ADMIN", "BRANCH_MANAGER");

    private final PaymentRepository paymentRepository;
    private final PaymentRefundRepository refundRepository;
    private final OrderApi orderApi;
    private final AuditApi auditApi;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public PaymentRefundService(
            PaymentRepository paymentRepository,
            PaymentRefundRepository refundRepository,
            OrderApi orderApi,
            AuditApi auditApi,
            ObjectMapper objectMapper,
            Clock clock) {
        this.paymentRepository = paymentRepository;
        this.refundRepository = refundRepository;
        this.orderApi = orderApi;
        this.auditApi = auditApi;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional
    public RefundResponse createRefund(
            FlowerChainPrincipal actor,
            UUID paymentId,
            BigDecimal refundAmount,
            String reason,
            String externalRefundRef) {
        requireRefundRole(actor);
        if (refundAmount == null || refundAmount.signum() <= 0 || refundAmount.scale() > 2) {
            throw FlowerChainException.badRequest(
                    "REFUND_AMOUNT_INVALID", "The refund amount must be greater than 0 with at most 2 decimal places.");
        }

        // Row lock: concurrent refunds of one payment run one after another, so the limit below always holds.
        PaymentEntity payment = paymentRepository.findByIdForUpdate(paymentId)
                .orElseThrow(() -> paymentNotFound(paymentId));
        OrderForPaymentDto order = orderApi.getOrderForPaymentOrThrow(payment.getOrderId());
        if (!actor.canAccessBranch(order.processingBranchId())) {
            throw paymentNotFound(paymentId); // 404, not 403: do not confirm that the payment exists
        }

        if (PaymentEntity.STATUS_REFUNDED.equals(payment.getStatus())) {
            throw FlowerChainException.conflict(
                    "PAYMENT_ALREADY_FULLY_REFUNDED",
                    "Payment already refunded",
                    "This payment has already been refunded in full.");
        }
        if (!PaymentEntity.STATUS_CAPTURED.equals(payment.getStatus())) {
            throw FlowerChainException.conflict(
                    "PAYMENT_NOT_CAPTURED",
                    "Payment not captured",
                    "Only a captured payment can be refunded; this payment is " + payment.getStatus() + ".");
        }

        BigDecimal alreadyRefunded = refundRepository.sumRefundAmountByPaymentId(paymentId);
        if (alreadyRefunded == null) {
            alreadyRefunded = BigDecimal.ZERO;
        }
        BigDecimal refundable = payment.getAmount().subtract(alreadyRefunded);
        if (refundAmount.compareTo(refundable) > 0) {
            throw FlowerChainException.conflict(
                    "REFUND_EXCEEDS_CAPTURED_AMOUNT",
                    "Refund exceeds captured amount",
                    "The refund amount " + refundAmount.toPlainString() + " exceeds the refundable amount "
                            + refundable.toPlainString() + " (captured " + payment.getAmount().toPlainString()
                            + ", already refunded " + alreadyRefunded.toPlainString() + ").");
        }

        Instant now = Instant.now(clock);
        BigDecimal amount = refundAmount.setScale(2);
        String trimmedReason = reason.strip();
        String reference = externalRefundRef == null || externalRefundRef.isBlank() ? null : externalRefundRef.strip();
        PaymentRefundEntity refund = refundRepository.saveAndFlush(new PaymentRefundEntity(
                UUID.randomUUID(), paymentId, order.orderId(), amount, trimmedReason, reference, actor.actorId(), now));

        BigDecimal refundedTotal = alreadyRefunded.add(amount);
        boolean fullRefund = refundedTotal.compareTo(payment.getAmount()) == 0;
        String previousPaymentStatus = payment.getStatus();
        if (fullRefund) {
            payment.markRefunded(now);
            paymentRepository.save(payment);
        }

        orderApi.recordOrderRefunded(order.orderId(), amount, fullRefund, actor.actorId(), trimmedReason);

        Map<String, Object> oldValues = new LinkedHashMap<>();
        oldValues.put("paymentStatus", previousPaymentStatus);
        oldValues.put("refundedTotal", alreadyRefunded.toPlainString());
        Map<String, Object> newValues = new LinkedHashMap<>();
        newValues.put("paymentStatus", payment.getStatus());
        newValues.put("refundedTotal", refundedTotal.toPlainString());
        newValues.put("refundAmount", amount.toPlainString());
        newValues.put("fullRefund", fullRefund);
        newValues.put("reason", trimmedReason);
        newValues.put("externalRefundRef", reference);
        newValues.put("paymentId", paymentId.toString());
        newValues.put("orderId", order.orderId().toString());
        auditApi.record(new AuditEntryCommand(
                "REFUND",
                "PAYMENT_REFUND_RECORDED",
                order.processingBranchId(),
                "PAYMENT_REFUND",
                refund.getId(),
                actor.actorId(),
                "STAFF",
                objectMapper.writeValueAsString(oldValues),
                objectMapper.writeValueAsString(newValues)));

        return new RefundResponse(
                refund.getId(),
                refund.getPaymentId(),
                refund.getOrderId(),
                refund.getRefundAmount().toPlainString(),
                refund.getReason(),
                refund.getExternalRefundRef(),
                refund.getApprovedByStaffId(),
                refund.getRefundedAt());
    }

    private static void requireRefundRole(FlowerChainPrincipal actor) {
        boolean allowed = actor != null
                && "STAFF".equals(actor.actorType())
                && REFUND_ROLES.stream().anyMatch(actor::hasRole);
        if (!allowed) {
            throw FlowerChainException.forbidden(
                    "AUTH_FORBIDDEN_ROLE", "Your role is not permitted to record refunds.");
        }
    }

    private static FlowerChainException paymentNotFound(UUID paymentId) {
        return FlowerChainException.notFound("Payment " + paymentId + " not found.");
    }
}
