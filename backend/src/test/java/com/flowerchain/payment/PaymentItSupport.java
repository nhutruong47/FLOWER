package com.flowerchain.payment;

import static com.flowerchain.payment.WebhookTestSignatures.SECRET;
import static com.flowerchain.payment.WebhookTestSignatures.signedHeaders;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;

/**
 * Shared set-up for the payment integration tests: the real application on a random port, the sandbox-hmac
 * adapter enabled with the test secret, and one PostgreSQL 16 container for all subclasses (singleton container,
 * so the cached Spring context never points at a stopped database). Requires Docker: CI only (DEC-015).
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "flowerchain.payment.sandbox-hmac.enabled=true",
            "flowerchain.payment.sandbox-hmac.webhook-secret=" + WebhookTestSignatures.SECRET
        })
abstract class PaymentItSupport {

    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    static {
        POSTGRES.start();
    }

    protected static final String PROVIDER = "sandbox-hmac";
    protected static final BigDecimal TOTAL = new BigDecimal("500000.00");

    @LocalServerPort
    private int port;

    @Autowired
    private DataSource dataSource;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    protected final HttpClient http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
    protected JdbcTemplate jdbc;

    protected record SeededOrder(UUID orderId, UUID customerId, UUID branchId) {}

    @BeforeEach
    void setUpJdbc() {
        jdbc = new JdbcTemplate(dataSource);
    }

    // ------------------------------------------------------------------ seeding

    protected UUID seedBranch() {
        UUID branchId = UUID.randomUUID();
        String suffix = shortId();
        jdbc.update(
                "INSERT INTO branches (id, code, name, street_address, district, city, phone, latitude, longitude, "
                        + "operating_hours) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                branchId, "IT-" + suffix, "IT Branch " + suffix, "1 Test Street", "District 1", "Ho Chi Minh City",
                "0280000000", new BigDecimal("10.776900"), new BigDecimal("106.700900"), "08:00-20:00");
        return branchId;
    }

    protected SeededOrder seedPendingOrder() {
        return seedOrder("PENDING_PAYMENT", "UNPAID");
    }

    protected SeededOrder seedOrder(String status, String paymentStatus) {
        UUID branchId = seedBranch();
        UUID customerId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO customers (id, email, password_hash, full_name, phone) VALUES (?, ?, ?, ?, ?)",
                customerId, "it-" + shortId() + "@example.test", "not-a-real-hash", "IT Customer", "0900000000");
        jdbc.update(
                "INSERT INTO orders (id, order_number, customer_id, processing_branch_id, fulfilment_type, status, "
                        + "payment_status, currency_code, items_subtotal, delivery_fee, discount_amount, total_amount, "
                        + "recipient_name, recipient_phone) "
                        + "VALUES (?, ?, ?, ?, 'PICKUP', ?, ?, 'VND', ?, 0, 0, ?, ?, ?)",
                orderId, "IT-" + orderId, customerId, branchId, status, paymentStatus, TOTAL, TOTAL,
                "IT Recipient", "0900000000");
        return new SeededOrder(orderId, customerId, branchId);
    }

    /** A paid order with one captured payment of {@link #TOTAL}; returns the payment ID. */
    protected UUID seedCapturedPayment(SeededOrder order) {
        UUID paymentId = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO payments (id, order_id, provider, payment_method, provider_transaction_id, status, amount, "
                        + "currency_code, captured_at) VALUES (?, ?, ?, 'ONLINE_GATEWAY', ?, 'CAPTURED', ?, 'VND', now())",
                paymentId, order.orderId(), PROVIDER, "txn_seed_" + shortId(), TOTAL);
        return paymentId;
    }

    /** Creates an active staff user with the given roles and branches and logs in; returns the bearer token. */
    protected String loginAsStaff(Set<String> roles, Set<UUID> branchIds) throws Exception {
        UUID staffId = UUID.randomUUID();
        String email = "staff-" + shortId() + "@example.test";
        String password = "it-password-" + shortId();
        jdbc.update(
                "INSERT INTO staff_users (id, email, password_hash, full_name) VALUES (?, ?, ?, ?)",
                staffId, email, passwordEncoder.encode(password), "IT Staff");
        for (String role : roles) {
            jdbc.update("INSERT INTO staff_user_roles (id, staff_user_id, role_code) VALUES (?, ?, ?)",
                    UUID.randomUUID(), staffId, role);
        }
        for (UUID branchId : branchIds) {
            jdbc.update("INSERT INTO staff_branch_assignments (id, staff_user_id, branch_id) VALUES (?, ?, ?)",
                    UUID.randomUUID(), staffId, branchId);
        }
        String body = objectMapper.writeValueAsString(Map.of("email", email, "password", password));
        HttpResponse<String> response = http.send(
                HttpRequest.newBuilder(url("/api/v1/admin/auth/login"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IllegalStateException("Staff login failed: " + response.statusCode());
        }
        return objectMapper.readTree(response.body()).path("accessToken").asString();
    }

    // ------------------------------------------------------------------ HTTP

    protected HttpResponse<String> postWebhook(String provider, byte[] body, Map<String, String> headers)
            throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(url("/api/v1/webhooks/payments/" + provider))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(body));
        headers.forEach(request::header);
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    /** A webhook signed now with the v1 scheme and the configured test secret. */
    protected HttpResponse<String> postSignedWebhook(byte[] body) throws Exception {
        return postWebhook(PROVIDER, body, signedHeaders(SECRET, Instant.now().getEpochSecond(), body));
    }

    protected HttpResponse<String> postRefund(String token, UUID paymentId, String idempotencyKey, String amount)
            throws Exception {
        String body = objectMapper.writeValueAsString(
                Map.of("refundAmount", amount, "reason", "Customer cancelled the order", "externalRefundRef", "BANK-1"));
        return http.send(
                HttpRequest.newBuilder(url("/api/v1/admin/payments/" + paymentId + "/refunds"))
                        .header("Content-Type", "application/json")
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", idempotencyKey)
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    protected String outcome(HttpResponse<String> response) {
        return objectMapper.readTree(response.body()).path("outcome").asString();
    }

    protected String jsonField(HttpResponse<String> response, String field) {
        return objectMapper.readTree(response.body()).path(field).asString();
    }

    private URI url(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    // ------------------------------------------------------------------ assertions support

    protected long count(String sql, Object... args) {
        Long value = jdbc.queryForObject(sql, Long.class, args);
        return value == null ? 0 : value;
    }

    protected Map<String, Object> orderRow(UUID orderId) {
        return jdbc.queryForMap(
                "SELECT status, payment_status, processing_branch_id FROM orders WHERE id = ?", orderId);
    }

    protected interface Task<T> {
        T run(int index) throws Exception;
    }

    /** Starts all tasks at the same moment and returns each result, or the exception it threw. */
    protected static <T> List<Object> runConcurrently(int count, Task<T> task) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(count);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Object>> futures = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                int index = i;
                Callable<Object> callable = () -> {
                    start.await();
                    try {
                        return task.run(index);
                    } catch (Exception e) {
                        return e;
                    }
                };
                futures.add(pool.submit(callable));
            }
            start.countDown();
            List<Object> results = new ArrayList<>();
            for (Future<Object> future : futures) {
                results.add(future.get(60, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }

    @SuppressWarnings("unchecked")
    protected static List<HttpResponse<String>> responses(List<Object> results) {
        List<HttpResponse<String>> responses = new ArrayList<>();
        for (Object result : results) {
            if (!(result instanceof HttpResponse<?>)) {
                throw new AssertionError("Expected an HTTP response but got " + result);
            }
            responses.add((HttpResponse<String>) result);
        }
        return responses;
    }

    protected static String shortId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
