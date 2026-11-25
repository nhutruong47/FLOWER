package com.flowerchain.payment.web;

import com.flowerchain.payment.application.PaymentWebhookService;
import com.flowerchain.payment.application.WebhookAckResponse;
import com.flowerchain.shared.api.FlowerChainException;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * Server-to-server payment webhooks (FR-025, FR-026). Public route ({@code permitAll} in SecurityConfig): the
 * caller is authenticated by the webhook signature, which is checked on the raw body bytes before anything is
 * parsed or stored (REQ-009). A browser redirect never reaches this code and is never proof of payment.
 */
@RestController
public class PaymentWebhookController {

    /** Upper bound on the webhook body; larger bodies are rejected without being buffered. */
    static final int MAX_BODY_BYTES = 64 * 1024;

    private final PaymentWebhookService webhookService;

    public PaymentWebhookController(PaymentWebhookService webhookService) {
        this.webhookService = webhookService;
    }

    @PostMapping("/api/v1/webhooks/payments/{provider}")
    public WebhookAckResponse receivePaymentWebhook(
            @PathVariable("provider") String provider,
            @RequestHeader HttpHeaders headers,
            HttpServletRequest request) throws IOException {
        byte[] rawBody = readRawBody(request);
        return webhookService.receive(provider, rawBody, headers);
    }

    private static byte[] readRawBody(HttpServletRequest request) throws IOException {
        if (request.getContentLengthLong() > MAX_BODY_BYTES) {
            throw bodyTooLarge();
        }
        byte[] body = request.getInputStream().readNBytes(MAX_BODY_BYTES + 1);
        if (body.length > MAX_BODY_BYTES) {
            throw bodyTooLarge();
        }
        return body;
    }

    private static FlowerChainException bodyTooLarge() {
        return new FlowerChainException(
                HttpStatus.CONTENT_TOO_LARGE,
                "PAYMENT_WEBHOOK_PAYLOAD_TOO_LARGE",
                "Webhook payload too large",
                "The webhook body exceeds " + MAX_BODY_BYTES + " bytes.");
    }
}
