package com.flowerchain.payment;

import static com.flowerchain.payment.WebhookTestSignatures.OTHER_SECRET;
import static com.flowerchain.payment.WebhookTestSignatures.SECRET;
import static com.flowerchain.payment.WebhookTestSignatures.payload;
import static com.flowerchain.payment.WebhookTestSignatures.sign;
import static com.flowerchain.payment.WebhookTestSignatures.signBodyOnly;
import static com.flowerchain.payment.WebhookTestSignatures.signedHeaders;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * TEST-011 (REQ-009) and TEST-012 (REQ-010) against the real application, HTTP stack, security filter chain and
 * PostgreSQL 16. Requires Docker, so it runs in CI only (DEC-015).
 */
class PaymentWebhookIT extends PaymentItSupport {

    // ---------------------------------------------------------------- TEST-011: forged webhooks (REQ-009)

    @Test
    void test011_forgedMissingTamperedUntimestampedAndStaleSignaturesWriteNothing() throws Exception {
        UUID orderId = seedPendingOrder().orderId();
        String eventId = "evt_forged_" + shortId();
        String transactionId = "txn_forged_" + shortId();
        byte[] body = payload(eventId, transactionId, orderId, "CAPTURED", "500000.00", "VND");
        long now = Instant.now().getEpochSecond();
        long eventsBefore = count("SELECT count(*) FROM payment_webhook_events");
        long paymentsBefore = count("SELECT count(*) FROM payments");
        long auditsBefore = count("SELECT count(*) FROM audit_logs");

        HttpResponse<String> forged = postWebhook(PROVIDER, body, signedHeaders(OTHER_SECRET, now, body));
        HttpResponse<String> missingSignature =
                postWebhook(PROVIDER, body, Map.of("X-Payment-Timestamp", Long.toString(now)));
        byte[] tampered = payload(eventId, transactionId, orderId, "CAPTURED", "1.00", "VND");
        HttpResponse<String> tamperedBody = postWebhook(PROVIDER, tampered, signedHeaders(SECRET, now, body));
        HttpResponse<String> noTimestamp =
                postWebhook(PROVIDER, body, Map.of("X-Payment-Signature", signBodyOnly(SECRET, body)));
        HttpResponse<String> stale = postWebhook(PROVIDER, body, signedHeaders(SECRET, now - 3600, body));

        for (HttpResponse<String> response : List.of(forged, missingSignature, tamperedBody, noTimestamp, stale)) {
            assertThat(response.statusCode()).isEqualTo(401);
            assertThat(response.headers().firstValue("Content-Type")).hasValueSatisfying(
                    type -> assertThat(type).startsWith("application/problem+json"));
            assertThat(response.body()).contains("PAYMENT_WEBHOOK_SIGNATURE_INVALID");
        }
        assertThat(count("SELECT count(*) FROM payment_webhook_events")).isEqualTo(eventsBefore);
        assertThat(count("SELECT count(*) FROM payments")).isEqualTo(paymentsBefore);
        assertThat(count("SELECT count(*) FROM audit_logs")).isEqualTo(auditsBefore);
        assertThat(count("SELECT count(*) FROM order_status_history WHERE order_id = ?", orderId)).isZero();
        assertOrderState(orderId, "PENDING_PAYMENT", "UNPAID");
    }

    @Test
    void test011_undecidedProvidersAreRejectedWithoutWritingAnything() throws Exception {
        UUID orderId = seedPendingOrder().orderId();
        byte[] body = payload("evt_vnpay_" + shortId(), "txn_vnpay_" + shortId(), orderId, "CAPTURED", "500000.00", "VND");
        long eventsBefore = count("SELECT count(*) FROM payment_webhook_events");

        for (String provider : List.of("vnpay", "momo")) {
            HttpResponse<String> response =
                    postWebhook(provider, body, signedHeaders(SECRET, Instant.now().getEpochSecond(), body));
            assertThat(response.statusCode()).isEqualTo(400);
            assertThat(response.body()).contains("PAYMENT_PROVIDER_NOT_SUPPORTED");
        }
        assertThat(count("SELECT count(*) FROM payment_webhook_events")).isEqualTo(eventsBefore);
        assertOrderState(orderId, "PENDING_PAYMENT", "UNPAID");
    }

    // ---------------------------------------------------------------- TEST-012: duplicates (REQ-010)

    @Test
    void test012_sameEventDeliveredTwiceSequentiallyIsProcessedOnce() throws Exception {
        UUID orderId = seedPendingOrder().orderId();
        String eventId = "evt_seq_" + shortId();
        String transactionId = "txn_seq_" + shortId();
        byte[] body = payload(eventId, transactionId, orderId, "CAPTURED", "500000", "VND");
        Map<String, String> headers = signedHeaders(SECRET, Instant.now().getEpochSecond(), body);

        HttpResponse<String> first = postWebhook(PROVIDER, body, headers);
        HttpResponse<String> second = postWebhook(PROVIDER, body, headers);

        assertThat(first.statusCode()).isEqualTo(200);
        assertThat(outcome(first)).isEqualTo("PROCESSED");
        assertThat(second.statusCode()).isEqualTo(200);
        assertThat(outcome(second)).isEqualTo("ALREADY_PROCESSED_IDEMPOTENT");
        assertProcessedExactlyOnce(orderId, eventId, transactionId);
    }

    @Test
    void test012_sameEventDeliveredConcurrentlyIsProcessedOnce() throws Exception {
        UUID orderId = seedPendingOrder().orderId();
        String eventId = "evt_conc_" + shortId();
        String transactionId = "txn_conc_" + shortId();
        byte[] body = payload(eventId, transactionId, orderId, "CAPTURED", "500000.00", "VND");
        Map<String, String> headers = signedHeaders(SECRET, Instant.now().getEpochSecond(), body);

        List<HttpResponse<String>> responses = responses(runConcurrently(8, i -> postWebhook(PROVIDER, body, headers)));

        assertThat(responses).allSatisfy(r -> assertThat(r.statusCode()).isEqualTo(200));
        List<String> outcomes = responses.stream().map(this::outcome).toList();
        assertThat(outcomes).filteredOn("PROCESSED"::equals).hasSize(1);
        assertThat(outcomes).filteredOn("ALREADY_PROCESSED_IDEMPOTENT"::equals).hasSize(7);
        assertProcessedExactlyOnce(orderId, eventId, transactionId);
    }

    @Test
    void test012_sameTransactionUnderDifferentEventIdsConcurrentlyIsPaidOnce() throws Exception {
        UUID orderId = seedPendingOrder().orderId();
        String transactionId = "txn_multi_" + shortId();
        String eventPrefix = "evt_multi_" + shortId() + "_";

        List<HttpResponse<String>> responses = responses(runConcurrently(4, i -> postSignedWebhook(
                payload(eventPrefix + i, transactionId, orderId, "CAPTURED", "500000.00", "VND"))));

        assertThat(responses).allSatisfy(r -> assertThat(r.statusCode()).isEqualTo(200));
        List<String> outcomes = responses.stream().map(this::outcome).toList();
        assertThat(outcomes).filteredOn("PROCESSED"::equals).hasSize(1);
        assertThat(outcomes).filteredOn("ALREADY_PROCESSED_IDEMPOTENT"::equals).hasSize(3);
        assertThat(count("SELECT count(*) FROM payment_webhook_events WHERE provider_transaction_id = ?", transactionId))
                .isEqualTo(4);
        assertThat(count("SELECT count(*) FROM payments WHERE provider = ? AND provider_transaction_id = ?",
                        PROVIDER, transactionId))
                .isEqualTo(1);
        assertOrderPaidOnce(orderId);
    }

    @Test
    void test012_databaseRejectsDuplicateEventAndTransactionIds() {
        UUID orderId = seedPendingOrder().orderId();
        String eventId = "evt_sql_" + shortId();
        String transactionId = "txn_sql_" + shortId();
        String insertEvent = "INSERT INTO payment_webhook_events "
                + "(id, provider, provider_event_id, provider_transaction_id, order_id, signature_verified, processing_outcome) "
                + "VALUES (?, ?, ?, ?, ?, true, 'PROCESSED')";
        String insertPayment = "INSERT INTO payments "
                + "(id, order_id, provider, payment_method, provider_transaction_id, status, amount, currency_code) "
                + "VALUES (?, ?, ?, 'ONLINE_GATEWAY', ?, 'CAPTURED', ?, 'VND')";

        jdbc.update(insertEvent, UUID.randomUUID(), PROVIDER, eventId, transactionId, orderId);
        jdbc.update(insertPayment, UUID.randomUUID(), orderId, PROVIDER, transactionId, TOTAL);

        assertThatThrownBy(() -> jdbc.update(insertEvent, UUID.randomUUID(), PROVIDER, eventId, transactionId, orderId))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(insertPayment, UUID.randomUUID(), orderId, PROVIDER, transactionId, TOTAL))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // ---------------------------------------------------------------- business checks

    @Test
    void amountMismatchIsRecordedAndAuditedButNeverMarksTheOrderPaid() throws Exception {
        UUID orderId = seedPendingOrder().orderId();
        String eventId = "evt_amount_" + shortId();
        byte[] body = payload(eventId, "txn_amount_" + shortId(), orderId, "CAPTURED", "1000.00", "VND");

        HttpResponse<String> response = postSignedWebhook(body);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(outcome(response)).isEqualTo("REJECTED_AMOUNT_MISMATCH");
        assertOrderState(orderId, "PENDING_PAYMENT", "UNPAID");
        assertThat(count("SELECT count(*) FROM payments WHERE order_id = ?", orderId)).isZero();
        assertThat(jdbc.queryForObject(
                        "SELECT processing_outcome FROM payment_webhook_events WHERE provider = ? AND provider_event_id = ?",
                        String.class, PROVIDER, eventId))
                .isEqualTo("REJECTED_AMOUNT_MISMATCH");
        assertThat(count("SELECT count(*) FROM audit_logs WHERE target_entity_id = ? AND action = ?",
                        orderId, "PAYMENT_WEBHOOK_AMOUNT_MISMATCH"))
                .isEqualTo(1);
    }

    @Test
    void v1SignatureWithPaddedHeadersIsAccepted() throws Exception {
        UUID orderId = seedPendingOrder().orderId();
        String eventId = "evt_ts_" + shortId();
        String transactionId = "txn_ts_" + shortId();
        byte[] body = payload(eventId, transactionId, orderId, "CAPTURED", "500000.00", "VND");
        String timestamp = Long.toString(Instant.now().getEpochSecond());
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("X-Payment-Signature", sign(SECRET, timestamp, body).toUpperCase());
        headers.put("X-Payment-Timestamp", timestamp);

        HttpResponse<String> response = postWebhook(PROVIDER, body, headers);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(outcome(response)).isEqualTo("PROCESSED");
        assertProcessedExactlyOnce(orderId, eventId, transactionId);
    }

    // ---------------------------------------------------------------- helpers

    private void assertProcessedExactlyOnce(UUID orderId, String eventId, String transactionId) {
        assertThat(count("SELECT count(*) FROM payment_webhook_events WHERE provider = ? AND provider_event_id = ?",
                        PROVIDER, eventId))
                .isEqualTo(1);
        assertThat(count("SELECT count(*) FROM payments WHERE provider = ? AND provider_transaction_id = ?",
                        PROVIDER, transactionId))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject(
                        "SELECT status FROM payments WHERE provider = ? AND provider_transaction_id = ?",
                        String.class, PROVIDER, transactionId))
                .isEqualTo("CAPTURED");
        assertOrderPaidOnce(orderId);
    }

    private void assertOrderPaidOnce(UUID orderId) {
        assertOrderState(orderId, "CONFIRMED", "PAID");
        assertThat(count("SELECT count(*) FROM order_status_history WHERE order_id = ? AND to_status = 'CONFIRMED'",
                        orderId))
                .isEqualTo(1);
        assertThat(count("SELECT count(*) FROM audit_logs WHERE target_entity_id = ? AND action = 'ORDER_PAID_WEBHOOK'",
                        orderId))
                .isEqualTo(1);
        assertThat(count("SELECT count(*) FROM payments WHERE order_id = ? AND status = 'CAPTURED'", orderId))
                .isEqualTo(1);
    }

    private void assertOrderState(UUID orderId, String status, String paymentStatus) {
        Map<String, Object> row = orderRow(orderId);
        assertThat(row.get("status")).isEqualTo(status);
        assertThat(row.get("payment_status")).isEqualTo(paymentStatus);
    }
}
