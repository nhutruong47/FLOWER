package com.flowerchain.payment.application;

import com.flowerchain.payment.gateway.PaymentGatewayPort;
import com.flowerchain.payment.gateway.VerifiedPaymentEvent;
import com.flowerchain.shared.api.FlowerChainException;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;

/**
 * Entry point for payment webhooks (FR-025, FR-026). Deliberately not transactional:
 *
 * <ol>
 *   <li>{@link #resolveGateway} rejects unsupported or disabled providers; the controller calls it before it reads
 *       the request body;
 *   <li>{@link #receive} verifies the signature on the raw bytes, then parses (REQ-009) - nothing is persisted
 *       before this;
 *   <li>the event is processed exactly once (REQ-010). When two deliveries race, the loser's transaction fails on a
 *       unique constraint and is rolled back completely; the violation is handled here, outside that transaction,
 *       by checking whether the event is now recorded.
 * </ol>
 */
@Service
public class PaymentWebhookService {

    private static final Logger log = LoggerFactory.getLogger(PaymentWebhookService.class);
    private static final int MAX_ATTEMPTS = 2;

    private final Map<String, PaymentGatewayPort> gatewaysByProvider;
    private final PaymentWebhookEventProcessor processor;

    /** Gateways are optional beans: a disabled adapter (sandbox-hmac by default) is simply not present. */
    public PaymentWebhookService(ObjectProvider<PaymentGatewayPort> gateways, PaymentWebhookEventProcessor processor) {
        this.gatewaysByProvider = gateways.orderedStream()
                .collect(Collectors.toUnmodifiableMap(PaymentGatewayPort::providerCode, Function.identity()));
        this.processor = processor;
    }

    /** Returns the enabled adapter for the path segment, or 400 for an unsupported or disabled provider. */
    public PaymentGatewayPort resolveGateway(String provider) {
        String code = provider == null ? "" : provider.strip().toLowerCase(Locale.ROOT);
        PaymentGatewayPort gateway = gatewaysByProvider.get(code);
        if (gateway == null) {
            // vnpay, momo, ... stay unsupported until the provider question (B4) is answered; sandbox-hmac is
            // unsupported unless explicitly enabled.
            throw FlowerChainException.badRequest(
                    "PAYMENT_PROVIDER_NOT_SUPPORTED", "The payment provider in the request path is not supported.");
        }
        return gateway;
    }

    public WebhookAckResponse receive(PaymentGatewayPort gateway, byte[] rawBody, HttpHeaders headers) {
        VerifiedPaymentEvent event = gateway.verifyAndParse(rawBody, headers);
        WebhookOutcome outcome = processExactlyOnce(event);
        log.info("Payment webhook {} event {} for order {}: {}",
                event.provider(), event.eventId(), event.orderId(), outcome);
        return new WebhookAckResponse(event.eventId(), outcome);
    }

    WebhookOutcome processExactlyOnce(VerifiedPaymentEvent event) {
        DataIntegrityViolationException lastViolation = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                return processor.process(event);
            } catch (DataIntegrityViolationException violation) {
                // The attempt's transaction has been rolled back as a whole; we are outside it now.
                if (processor.isEventRecorded(event.provider(), event.eventId())) {
                    return WebhookOutcome.ALREADY_PROCESSED_IDEMPOTENT;
                }
                // A concurrent event for the same transaction ID won; retry once so the committed payment is seen.
                lastViolation = violation;
            }
        }
        throw lastViolation;
    }
}
