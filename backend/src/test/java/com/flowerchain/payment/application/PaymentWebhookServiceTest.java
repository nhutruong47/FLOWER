package com.flowerchain.payment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.flowerchain.payment.gateway.PaymentGatewayPort;
import com.flowerchain.payment.gateway.VerifiedPaymentEvent;
import com.flowerchain.shared.api.FlowerChainException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;

/** Provider resolution, verify-before-process, and race handling outside the failed transaction (REQ-009/010). */
@ExtendWith(MockitoExtension.class)
class PaymentWebhookServiceTest {

    private static final byte[] BODY = "{}".getBytes(StandardCharsets.UTF_8);
    private static final VerifiedPaymentEvent EVENT = new VerifiedPaymentEvent(
            "sandbox-hmac",
            "evt_1001",
            "txn_9001",
            UUID.fromString("00000000-0000-0000-0000-00000000a001"),
            VerifiedPaymentEvent.Status.CAPTURED,
            new BigDecimal("500000"),
            "VND",
            Instant.parse("2026-11-25T07:59:00Z"));

    @Mock
    private PaymentGatewayPort sandboxGateway;

    @Mock
    private PaymentWebhookEventProcessor processor;

    private final HttpHeaders headers = new HttpHeaders();
    private PaymentWebhookService service;

    @BeforeEach
    void setUp() {
        when(sandboxGateway.providerCode()).thenReturn("sandbox-hmac");
        service = new PaymentWebhookService(List.of(sandboxGateway), processor);
    }

    @ParameterizedTest
    @ValueSource(strings = {"vnpay", "momo", "stripe", "", "sandbox"})
    void unsupportedProviderIsRejectedBeforeVerificationOrProcessing(String provider) {
        assertThatThrownBy(() -> service.receive(provider, BODY, headers))
                .isInstanceOfSatisfying(FlowerChainException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(e.getErrorCode()).isEqualTo("PAYMENT_PROVIDER_NOT_SUPPORTED");
                });
        verify(sandboxGateway, never()).verifyAndParse(any(), any());
        verifyNoInteractions(processor);
    }

    @Test
    void failedVerificationStopsEverythingBeforeProcessing() {
        when(sandboxGateway.verifyAndParse(BODY, headers)).thenThrow(
                FlowerChainException.unauthorized("PAYMENT_WEBHOOK_SIGNATURE_INVALID", "invalid"));

        assertThatThrownBy(() -> service.receive("sandbox-hmac", BODY, headers))
                .isInstanceOf(FlowerChainException.class);
        verifyNoInteractions(processor);
    }

    @Test
    void verifiedEventIsProcessedAndAcknowledged() {
        when(sandboxGateway.verifyAndParse(BODY, headers)).thenReturn(EVENT);
        when(processor.process(EVENT)).thenReturn(WebhookOutcome.PROCESSED);

        WebhookAckResponse ack = service.receive("SANDBOX-HMAC", BODY, headers);

        assertThat(ack).isEqualTo(new WebhookAckResponse("evt_1001", WebhookOutcome.PROCESSED));
    }

    @Test
    void concurrentDuplicateThatLosesTheUniqueRaceIsAcknowledgedAsAlreadyProcessed() {
        when(sandboxGateway.verifyAndParse(BODY, headers)).thenReturn(EVENT);
        when(processor.process(EVENT)).thenThrow(new DataIntegrityViolationException("uq_payment_webhook_provider_event_id"));
        when(processor.isEventRecorded("sandbox-hmac", "evt_1001")).thenReturn(true);

        WebhookAckResponse ack = service.receive("sandbox-hmac", BODY, headers);

        assertThat(ack.outcome()).isEqualTo(WebhookOutcome.ALREADY_PROCESSED_IDEMPOTENT);
        verify(processor, times(1)).process(EVENT);
    }

    @Test
    void violationOnTheTransactionIdIsRetriedOnceInAFreshTransaction() {
        when(sandboxGateway.verifyAndParse(BODY, headers)).thenReturn(EVENT);
        when(processor.process(EVENT))
                .thenThrow(new DataIntegrityViolationException("uq_payments_provider_transaction_id"))
                .thenReturn(WebhookOutcome.ALREADY_PROCESSED_IDEMPOTENT);
        when(processor.isEventRecorded("sandbox-hmac", "evt_1001")).thenReturn(false);

        WebhookAckResponse ack = service.receive("sandbox-hmac", BODY, headers);

        assertThat(ack.outcome()).isEqualTo(WebhookOutcome.ALREADY_PROCESSED_IDEMPOTENT);
        verify(processor, times(2)).process(EVENT);
    }

    @Test
    void persistentViolationIsRethrownAfterTwoAttempts() {
        when(sandboxGateway.verifyAndParse(BODY, headers)).thenReturn(EVENT);
        when(processor.process(EVENT)).thenThrow(new DataIntegrityViolationException("still failing"));
        when(processor.isEventRecorded("sandbox-hmac", "evt_1001")).thenReturn(false);

        assertThatThrownBy(() -> service.receive("sandbox-hmac", BODY, headers))
                .isInstanceOf(DataIntegrityViolationException.class);
        verify(processor, times(2)).process(EVENT);
    }
}
