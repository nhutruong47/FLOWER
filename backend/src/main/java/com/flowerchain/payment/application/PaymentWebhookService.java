package com.flowerchain.payment.application;

import com.flowerchain.payment.gateway.PaymentGatewayPort;
import com.flowerchain.payment.gateway.VerifiedPaymentEvent;
import com.flowerchain.shared.api.FlowerChainException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;

/**
 * Entry point for payment webhooks (FR-025, FR-026). Deliberately not transactional:
 *
 * <ol>
 *   <li>resolve the provider adapter (unsupported providers are rejected before the body is read);
 *   <li>verify the signature on the raw bytes, then parse (REQ-009) - nothing is persisted before this;
 *   <li>process the event exactly once (REQ-010). When two deliveries race, the loser's transaction fails on a
 *       unique constraint and is rolled back completely; the violation is handled here, outside that
 *       transaction, by checking whether the event is now recorded.
 * </ol>
 */
@Service
public class PaymentWebhookService {

    private static final Logger log = LoggerFactory.getLogger(PaymentWebhookService.class);
    private static final int MAX_ATTEMPTS = 2;

    private final Map<String, PaymentGatewayPort> gatewaysByProvider;
    private final PaymentWebhookEventProcessor processor;

    public PaymentWebhookService(List<PaymentGatewayPort> gateways, PaymentWebhookEventProcessor processor) {
        this.gatewaysByProvider = gateways.stream()
                .collect(Collectors.toUnmodifiableMap(PaymentGatewayPort::providerCode, Function.identity()));
        this.processor = processor;
    }

    public WebhookAckResponse receive(String provider, byte[] rawBody, HttpHeaders headers) {
        PaymentGatewayPort gateway = resolveGateway(provider);
        VerifiedPaymentEvent event = gateway.verifyAndParse(rawBody, headers);
        WebhookOutcome outcome = processExactlyOnce(event);
        log.info("Payment webhook {} event {} for order {}: {}",
                event.provider(), event.eventId(), event.orderId(), outcome);
        return new WebhookAckResponse(event.eventId(), outcome);
    }

    private PaymentGatewayPort resolveGateway(String provider) {
        String code = provider == null ? "" : provider.strip().toLowerCase(Locale.ROOT);
        PaymentGatewayPort gateway = gatewaysByProvider.get(code);
        if (gateway == null) {
            // vnpay, momo, ... stay unsupported until the provider question (B4) is answered.
            throw FlowerChainException.badRequest(
                    "PAYMENT_PROVIDER_NOT_SUPPORTED", "The payment provider in the request path is not supported.");
        }
        return gateway;
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
