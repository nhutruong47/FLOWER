package com.flowerchain.payment;

import static com.flowerchain.payment.WebhookTestSignatures.OTHER_SECRET;
import static com.flowerchain.payment.WebhookTestSignatures.SECRET;
import static com.flowerchain.payment.WebhookTestSignatures.payload;
import static com.flowerchain.payment.WebhookTestSignatures.sign;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;

/**
 * TEST-011 (REQ-009) and TEST-012 (REQ-010) against the real application, HTTP stack, security filter chain and
 * PostgreSQL 16. Requires Docker, so it runs in CI only (DEC-015).
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "flowerchain.payment.webhook-secret=" + WebhookTestSignatures.SECRET)
@Testcontainers
class PaymentWebhookIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    private static final String PROVIDER = "sandbox-hmac";
    private static final BigDecimal TOTAL = new BigDecimal("500000.00");

    @LocalServerPort
    private int port;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private ObjectMapper objectMapper;

    private final HttpClient http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        jdbc = new JdbcTemplate(dataSource);
    }

    // ---------------------------------------------------------------- TEST-011: forged webhooks (REQ-009)

    @Test
    void test011_forgedMissingTamperedAndStaleSignaturesAreRejectedWithoutWritingAnything() throws Exception {
        UUID orderId = seedPendingOrder();
        String eventId = "evt_forged_" + shortId();
        String transactionId = "txn_forged_" + shortId();
        byte[] body = payload(eventId, transactionId, orderId, "CAPTURED", "500000.00", "VND");
        long eventsBefore = count("SELECT count(*) FROM payment_webhook_events");
        long paymentsBefore = count("SELECT count(*) FROM payments");
        long auditsBefore = count("SELECT count(*) FROM audit_logs");

        HttpResponse<String> forged = post(PROVIDER, body, signatureHeaders(sign(OTHER_SECRET, body)));
        HttpResponse<String> missing = post(PROVIDER, body, Map.of());
        byte[] tampered = payload(eventId, transactionId, orderId, "CAPTURED", "1.00", "VND");
        HttpResponse<String> tamperedBody = post(PROVIDER, tampered, signatureHeaders(sign(SECRET, body)));
        String staleTimestamp = Long.toString(Instant.now().getEpochSecond() - 3600);
        Map<String, String> staleHeaders = new LinkedHashMap<>();
        staleHeaders.put("X-Payment-Signature", sign(SECRET, staleTimestamp, body));
        staleHeaders.put("X-Payment-Timestamp", staleTimestamp);
        HttpResponse<String> stale = post(PROVIDER, body, staleHeaders);

        for (HttpResponse<String> response : List.of(forged, missing, tamperedBody, stale)) {
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
        UUID orderId = seedPendingOrder();
        byte[] body = payload("evt_vnpay_" + shortId(), "txn_vnpay_" + shortId(), orderId, "CAPTURED", "500000.00", "VND");
        long eventsBefore = count("SELECT count(*) FROM payment_webhook_events");

        for (String provider : List.of("vnpay", "momo")) {
            HttpResponse<String> response = post(provider, body, signatureHeaders(sign(SECRET, body)));
            assertThat(response.statusCode()).isEqualTo(400);
            assertThat(response.body()).contains("PAYMENT_PROVIDER_NOT_SUPPORTED");
        }
        assertThat(count("SELECT count(*) FROM payment_webhook_events")).isEqualTo(eventsBefore);
        assertOrderState(orderId, "PENDING_PAYMENT", "UNPAID");
    }

    // ---------------------------------------------------------------- TEST-012: duplicates (REQ-010)

    @Test
    void test012_sameEventDeliveredTwiceSequentiallyIsProcessedOnce() throws Exception {
        UUID orderId = seedPendingOrder();
        String eventId = "evt_seq_" + shortId();
        String transactionId = "txn_seq_" + shortId();
        byte[] body = payload(eventId, transactionId, orderId, "CAPTURED", "500000", "VND");
        Map<String, String> headers = signatureHeaders(sign(SECRET, body));

        HttpResponse<String> first = post(PROVIDER, body, headers);
        HttpResponse<String> second = post(PROVIDER, body, headers);

        assertThat(first.statusCode()).isEqualTo(200);
        assertThat(outcome(first)).isEqualTo("PROCESSED");
        assertThat(second.statusCode()).isEqualTo(200);
        assertThat(outcome(second)).isEqualTo("ALREADY_PROCESSED_IDEMPOTENT");
        assertProcessedExactlyOnce(orderId, eventId, transactionId);
    }

    @Test
    void test012_sameEventDeliveredConcurrentlyIsProcessedOnce() throws Exception {
        UUID orderId = seedPendingOrder();
        String eventId = "evt_conc_" + shortId();
        String transactionId = "txn_conc_" + shortId();
        byte[] body = payload(eventId, transactionId, orderId, "CAPTURED", "500000.00", "VND");
        Map<String, String> headers = signatureHeaders(sign(SECRET, body));

        List<HttpResponse<String>> responses = sendConcurrently(8, i -> post(PROVIDER, body, headers));

        assertThat(responses).allSatisfy(r -> assertThat(r.statusCode()).isEqualTo(200));
        List<String> outcomes = responses.stream().map(this::outcome).toList();
        assertThat(outcomes).filteredOn("PROCESSED"::equals).hasSize(1);
        assertThat(outcomes).filteredOn("ALREADY_PROCESSED_IDEMPOTENT"::equals).hasSize(7);
        assertProcessedExactlyOnce(orderId, eventId, transactionId);
    }

    @Test
    void test012_sameTransactionUnderDifferentEventIdsConcurrentlyIsPaidOnce() throws Exception {
        UUID orderId = seedPendingOrder();
        String transactionId = "txn_multi_" + shortId();
        String eventPrefix = "evt_multi_" + shortId() + "_";

        List<HttpResponse<String>> responses = sendConcurrently(4, i -> {
            byte[] body = payload(eventPrefix + i, transactionId, orderId, "CAPTURED", "500000.00", "VND");
            return post(PROVIDER, body, signatureHeaders(sign(SECRET, body)));
        });

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
        UUID orderId = seedPendingOrder();
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
        UUID orderId = seedPendingOrder();
        String eventId = "evt_amount_" + shortId();
        byte[] body = payload(eventId, "txn_amount_" + shortId(), orderId, "CAPTURED", "1000.00", "VND");

        HttpResponse<String> response = post(PROVIDER, body, signatureHeaders(sign(SECRET, body)));

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
    void timestampSignedWebhookIsAccepted() throws Exception {
        UUID orderId = seedPendingOrder();
        String eventId = "evt_ts_" + shortId();
        String transactionId = "txn_ts_" + shortId();
        byte[] body = payload(eventId, transactionId, orderId, "CAPTURED", "500000.00", "VND");
        String timestamp = Long.toString(Instant.now().getEpochSecond());
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("X-Payment-Signature", sign(SECRET, timestamp, body));
        headers.put("X-Payment-Timestamp", timestamp);

        HttpResponse<String> response = post(PROVIDER, body, headers);

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
        Map<String, Object> row = jdbc.queryForMap("SELECT status, payment_status FROM orders WHERE id = ?", orderId);
        assertThat(row.get("status")).isEqualTo(status);
        assertThat(row.get("payment_status")).isEqualTo(paymentStatus);
    }

    private UUID seedPendingOrder() {
        UUID branchId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        String suffix = shortId();
        jdbc.update(
                "INSERT INTO branches (id, code, name, street_address, district, city, phone, latitude, longitude, "
                        + "operating_hours) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                branchId, "IT-" + suffix, "IT Branch " + suffix, "1 Test Street", "District 1", "Ho Chi Minh City",
                "0280000000", new BigDecimal("10.776900"), new BigDecimal("106.700900"), "08:00-20:00");
        jdbc.update(
                "INSERT INTO customers (id, email, password_hash, full_name, phone) VALUES (?, ?, ?, ?, ?)",
                customerId, "it-" + suffix + "@example.test", "not-a-real-hash", "IT Customer", "0900000000");
        jdbc.update(
                "INSERT INTO orders (id, order_number, customer_id, processing_branch_id, fulfilment_type, status, "
                        + "payment_status, currency_code, items_subtotal, delivery_fee, discount_amount, total_amount, "
                        + "recipient_name, recipient_phone) "
                        + "VALUES (?, ?, ?, ?, 'PICKUP', 'PENDING_PAYMENT', 'UNPAID', 'VND', ?, 0, 0, ?, ?, ?)",
                orderId, "IT-" + orderId, customerId, branchId, TOTAL, TOTAL, "IT Recipient", "0900000000");
        return orderId;
    }

    private HttpResponse<String> post(String provider, byte[] body, Map<String, String> headers) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(
                        URI.create("http://localhost:" + port + "/api/v1/webhooks/payments/" + provider))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(body));
        headers.forEach(request::header);
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static Map<String, String> signatureHeaders(String signature) {
        return Map.of("X-Payment-Signature", signature);
    }

    private interface Delivery {
        HttpResponse<String> send(int index) throws Exception;
    }

    private static List<HttpResponse<String>> sendConcurrently(int count, Delivery delivery) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(count);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<HttpResponse<String>>> futures = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                int index = i;
                Callable<HttpResponse<String>> task = () -> {
                    start.await();
                    return delivery.send(index);
                };
                futures.add(pool.submit(task));
            }
            start.countDown();
            List<HttpResponse<String>> responses = new ArrayList<>();
            for (Future<HttpResponse<String>> future : futures) {
                responses.add(future.get(60, TimeUnit.SECONDS));
            }
            return responses;
        } finally {
            pool.shutdownNow();
        }
    }

    private String outcome(HttpResponse<String> response) {
        return objectMapper.readTree(response.body()).path("outcome").asString();
    }

    private long count(String sql, Object... args) {
        Long value = jdbc.queryForObject(sql, Long.class, args);
        return value == null ? 0 : value;
    }

    private static String shortId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
