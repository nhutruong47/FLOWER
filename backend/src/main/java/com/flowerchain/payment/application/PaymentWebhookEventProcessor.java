package com.flowerchain.payment.application;

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
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * Applies one verified payment event inside its own transaction (FR-026, REQ-010).
 *
 * <p>Exactly-once design:
 *
 * <ol>
 *   <li>The order row is locked first ({@code SELECT ... FOR UPDATE}), so every event of one order runs one
 *       after another and sees the committed result of the previous one.
 *   <li>An event ID that is already in {@code payment_webhook_events} returns
 *       {@link WebhookOutcome#ALREADY_PROCESSED_IDEMPOTENT} without writing anything.
 *   <li>Otherwise the decision is made, its effects are written, and the event row is inserted in the same
 *       transaction. {@code UNIQUE (provider, provider_event_id)} and
 *       {@code UNIQUE (provider, provider_transaction_id)} are the last line of defence: a violation rolls the
 *       whole transaction back and {@link PaymentWebhookService} handles it outside this transaction.
 * </ol>
 */
@Service
public class PaymentWebhookEventProcessor {

    static final String PAYMENT_METHOD_ONLINE_GATEWAY = "ONLINE_GATEWAY";

    private final OrderApi orderApi;
    private final AuditApi auditApi;
    private final PaymentRepository paymentRepository;
    private final PaymentWebhookEventRepository eventRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public PaymentWebhookEventProcessor(
            OrderApi orderApi,
            AuditApi auditApi,
            PaymentRepository paymentRepository,
            PaymentWebhookEventRepository eventRepository,
            ObjectMapper objectMapper,
            Clock clock) {
        this.orderApi = orderApi;
        this.auditApi = auditApi;
        this.paymentRepository = paymentRepository;
        this.eventRepository = eventRepository;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    /** Always a new transaction, so a constraint violation never leaves a caller's transaction rollback-only. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public WebhookOutcome process(VerifiedPaymentEvent event) {
        Instant now = Instant.now(clock);
        Optional<OrderForPaymentDto> lockedOrder = orderApi.lockOrderForPaymentProcessing(event.orderId());

        if (eventRepository.existsByProviderAndProviderEventId(event.provider(), event.eventId())) {
            return WebhookOutcome.ALREADY_PROCESSED_IDEMPOTENT;
        }

        if (lockedOrder.isEmpty()) {
            UUID eventRowId = recordEvent(event, null, WebhookOutcome.REJECTED_UNKNOWN_ORDER, now);
            audit(event, null, WebhookOutcome.REJECTED_UNKNOWN_ORDER, eventRowId);
            return WebhookOutcome.REJECTED_UNKNOWN_ORDER;
        }

        OrderForPaymentDto order = lockedOrder.get();
        PaymentEntity existingPayment = paymentRepository
                .findByProviderAndProviderTransactionId(event.provider(), event.transactionId())
                .orElse(null);

        WebhookOutcome outcome = apply(event, order, existingPayment, now);
        UUID eventRowId = recordEvent(event, order.orderId(), outcome, now);
        if (outcome != WebhookOutcome.ALREADY_PROCESSED_IDEMPOTENT
                && !(outcome == WebhookOutcome.PROCESSED && event.status() == VerifiedPaymentEvent.Status.CAPTURED)) {
            // A processed capture is audited by the order module (ORDER_PAID_WEBHOOK).
            audit(event, order, outcome, eventRowId);
        }
        return outcome;
    }

    /** Separate read in a fresh transaction, used after a failed (rolled back) attempt. */
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public boolean isEventRecorded(String provider, String eventId) {
        return eventRepository.existsByProviderAndProviderEventId(provider, eventId);
    }

    private WebhookOutcome apply(
            VerifiedPaymentEvent event, OrderForPaymentDto order, PaymentEntity existingPayment, Instant now) {
        boolean captured = event.status() == VerifiedPaymentEvent.Status.CAPTURED;

        if (existingPayment != null) {
            if (!existingPayment.getOrderId().equals(order.orderId())) {
                return WebhookOutcome.REJECTED_ORDER_MISMATCH;
            }
            if (captured && existingPayment.isCapturedOrRefunded()) {
                return WebhookOutcome.ALREADY_PROCESSED_IDEMPOTENT;
            }
            if (!captured && PaymentEntity.STATUS_FAILED.equals(existingPayment.getStatus())) {
                return WebhookOutcome.ALREADY_PROCESSED_IDEMPOTENT;
            }
            if (!captured && existingPayment.isCapturedOrRefunded()) {
                return WebhookOutcome.IGNORED_STALE; // FAILED after CAPTURED for the same transaction
            }
            // Otherwise: a capture after an earlier FAILED/INITIATED report, evaluated like a first capture.
        }

        if (!isAwaitingOnlinePayment(order)) {
            return WebhookOutcome.IGNORED_STALE; // e.g. capture for a cancelled order, FAILED after the order was paid
        }
        if (event.amount().compareTo(order.totalAmount()) != 0 || !event.currencyCode().equals(order.currencyCode())) {
            return WebhookOutcome.REJECTED_AMOUNT_MISMATCH;
        }

        if (!captured) {
            if (existingPayment != null) {
                existingPayment.markFailed(now);
                paymentRepository.saveAndFlush(existingPayment);
            } else {
                paymentRepository.saveAndFlush(newPayment(UUID.randomUUID(), event, order, PaymentEntity.STATUS_FAILED, null, now));
            }
            return WebhookOutcome.PROCESSED; // the order stays PENDING_PAYMENT; the customer may pay again
        }

        UUID paymentId = existingPayment != null ? existingPayment.getId() : UUID.randomUUID();
        VerifiedPaymentApplication application =
                orderApi.markOrderPaidByVerifiedWebhook(order.orderId(), paymentId, event.transactionId());
        if (application != VerifiedPaymentApplication.APPLIED) {
            return WebhookOutcome.IGNORED_STALE;
        }
        if (existingPayment != null) {
            existingPayment.markCaptured(event.amount(), event.currencyCode(), event.occurredAt(), now);
            paymentRepository.saveAndFlush(existingPayment);
        } else {
            paymentRepository.saveAndFlush(
                    newPayment(paymentId, event, order, PaymentEntity.STATUS_CAPTURED, event.occurredAt(), now));
        }
        return WebhookOutcome.PROCESSED;
    }

    private static boolean isAwaitingOnlinePayment(OrderForPaymentDto order) {
        return "PENDING_PAYMENT".equals(order.status()) && "UNPAID".equals(order.paymentStatus());
    }

    private static PaymentEntity newPayment(
            UUID paymentId,
            VerifiedPaymentEvent event,
            OrderForPaymentDto order,
            String status,
            Instant capturedAt,
            Instant now) {
        return new PaymentEntity(
                paymentId,
                order.orderId(),
                event.provider(),
                PAYMENT_METHOD_ONLINE_GATEWAY,
                event.transactionId(),
                status,
                event.amount(),
                event.currencyCode(),
                capturedAt,
                now);
    }

    private UUID recordEvent(VerifiedPaymentEvent event, UUID orderId, WebhookOutcome outcome, Instant now) {
        UUID id = UUID.randomUUID();
        eventRepository.saveAndFlush(new PaymentWebhookEventEntity(
                id, event.provider(), event.eventId(), event.transactionId(), orderId, outcome.name(), now));
        return id;
    }

    private void audit(VerifiedPaymentEvent event, OrderForPaymentDto order, WebhookOutcome outcome, UUID eventRowId) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("outcome", outcome.name());
        details.put("provider", event.provider());
        details.put("eventId", event.eventId());
        details.put("transactionId", event.transactionId());
        details.put("eventStatus", event.status().name());
        details.put("amount", event.amount().toPlainString());
        details.put("currencyCode", event.currencyCode());
        details.put("occurredAt", event.occurredAt().toString());
        details.put("orderId", event.orderId().toString());
        if (order != null) {
            details.put("orderStatus", order.status());
            details.put("orderPaymentStatus", order.paymentStatus());
            details.put("orderTotalAmount", order.totalAmount().toPlainString());
            details.put("orderCurrencyCode", order.currencyCode());
        }
        // Money captured at the provider that was not applied to the order needs a manual refund or follow-up.
        details.put("requiresManualReconciliation",
                event.status() == VerifiedPaymentEvent.Status.CAPTURED && outcome != WebhookOutcome.PROCESSED);

        auditApi.record(new AuditEntryCommand(
                "ORDER_STATUS",
                auditAction(event, outcome),
                order != null ? order.processingBranchId() : null,
                order != null ? "ORDER" : "PAYMENT_WEBHOOK_EVENT",
                order != null ? order.orderId() : eventRowId,
                null,
                "WEBHOOK",
                null,
                objectMapper.writeValueAsString(details)));
    }

    private static String auditAction(VerifiedPaymentEvent event, WebhookOutcome outcome) {
        return switch (outcome) {
            case PROCESSED -> event.status() == VerifiedPaymentEvent.Status.FAILED
                    ? "PAYMENT_FAILED_WEBHOOK"
                    : "PAYMENT_CAPTURED_WEBHOOK";
            case IGNORED_STALE -> "PAYMENT_WEBHOOK_IGNORED_STALE";
            case REJECTED_AMOUNT_MISMATCH -> "PAYMENT_WEBHOOK_AMOUNT_MISMATCH";
            case REJECTED_ORDER_MISMATCH -> "PAYMENT_WEBHOOK_ORDER_MISMATCH";
            case REJECTED_UNKNOWN_ORDER -> "PAYMENT_WEBHOOK_UNKNOWN_ORDER";
            case ALREADY_PROCESSED_IDEMPOTENT -> "PAYMENT_WEBHOOK_DUPLICATE";
        };
    }
}
