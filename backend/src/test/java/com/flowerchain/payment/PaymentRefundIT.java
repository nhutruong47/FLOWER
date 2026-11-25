package com.flowerchain.payment;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Refund endpoint end to end (FR-027, REQ-011, REQ-012, security review F4/F5/F9): real login, security chain,
 * idempotency, row locks and PostgreSQL. CI only (DEC-015).
 */
class PaymentRefundIT extends PaymentItSupport {

    @Test
    void headOfficeFullRefundIsRecordedAudited_andReplayedForTheSameKey() throws Exception {
        SeededOrder order = seedOrder("CONFIRMED", "PAID");
        UUID paymentId = seedCapturedPayment(order);
        String token = loginAsStaff(Set.of("HEAD_OFFICE_ADMIN"), Set.of());
        String key = "refund-" + shortId();

        HttpResponse<String> first = postRefund(token, paymentId, key, "500000");
        HttpResponse<String> retry = postRefund(token, paymentId, key, "500000");

        assertThat(first.statusCode()).isEqualTo(201);
        assertThat(retry.statusCode()).isEqualTo(201);
        assertThat(jsonField(retry, "id")).isEqualTo(jsonField(first, "id"));
        assertThat(refundRows(paymentId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT status FROM payments WHERE id = ?", String.class, paymentId))
                .isEqualTo("REFUNDED");
        assertThat(orderRow(order.orderId()).get("payment_status")).isEqualTo("REFUNDED");
        assertThat(count("SELECT count(*) FROM audit_logs WHERE category = 'REFUND' AND action = 'PAYMENT_REFUND_RECORDED' "
                        + "AND target_entity_id = ?", UUID.fromString(jsonField(first, "id"))))
                .isEqualTo(1);
    }

    @Test
    void branchManagersAndOtherNonGlobalRolesAreForbiddenAndNothingIsWritten() throws Exception {
        SeededOrder order = seedOrder("CONFIRMED", "PAID");
        UUID paymentId = seedCapturedPayment(order);
        UUID otherBranch = seedBranch();
        List<String> tokens = List.of(
                loginAsStaff(Set.of("BRANCH_MANAGER"), Set.of(order.branchId())), // own branch: still denied (F5, B20)
                loginAsStaff(Set.of("BRANCH_MANAGER"), Set.of(otherBranch)),
                loginAsStaff(Set.of("SALES_STAFF"), Set.of(order.branchId())));

        for (String token : tokens) {
            HttpResponse<String> response = postRefund(token, paymentId, "refund-" + shortId(), "1000");
            assertThat(response.statusCode()).isIn(403, 404);
            assertThat(response.body()).doesNotContain(order.orderId().toString());
        }
        assertThat(refundRows(paymentId)).isZero();
        assertThat(jdbc.queryForObject("SELECT status FROM payments WHERE id = ?", String.class, paymentId))
                .isEqualTo("CAPTURED");
        assertThat(orderRow(order.orderId()).get("payment_status")).isEqualTo("PAID");
    }

    @Test
    void concurrentRefundsThatTogetherExceedTheCapturedAmountOnlyOneSucceeds() throws Exception {
        SeededOrder order = seedOrder("CONFIRMED", "PAID");
        UUID paymentId = seedCapturedPayment(order);
        String token = loginAsStaff(Set.of("HEAD_OFFICE_ADMIN"), Set.of());

        List<HttpResponse<String>> responses = responses(runConcurrently(
                2, i -> postRefund(token, paymentId, "over-" + i + "-" + shortId(), "300000")));

        assertThat(responses).filteredOn(r -> r.statusCode() == 201).hasSize(1);
        assertThat(responses).filteredOn(r -> r.statusCode() == 409).hasSize(1)
                .allSatisfy(r -> assertThat(r.body()).contains("REFUND_EXCEEDS_CAPTURED_AMOUNT"));
        assertThat(refundRows(paymentId)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                        "SELECT sum(refund_amount) FROM payment_refunds WHERE payment_id = ?", BigDecimal.class, paymentId))
                .isEqualByComparingTo("300000.00");
    }

    @Test
    void concurrentRequestsWithTheSameIdempotencyKeyCreateOneRefund() throws Exception {
        SeededOrder order = seedOrder("CONFIRMED", "PAID");
        UUID paymentId = seedCapturedPayment(order);
        String token = loginAsStaff(Set.of("SYSTEM_OWNER"), Set.of());
        String key = "same-key-" + shortId();

        List<HttpResponse<String>> responses =
                responses(runConcurrently(4, i -> postRefund(token, paymentId, key, "100000")));

        assertThat(responses).allSatisfy(r -> assertThat(r.statusCode()).isIn(201, 409));
        List<HttpResponse<String>> created = responses.stream().filter(r -> r.statusCode() == 201).toList();
        assertThat(created).isNotEmpty();
        assertThat(created.stream().map(r -> jsonField(r, "id")).distinct()).hasSize(1);
        responses.stream().filter(r -> r.statusCode() == 409)
                .forEach(r -> assertThat(r.body()).contains("IDEMPOTENCY_REQUEST_IN_PROGRESS"));
        assertThat(responses).noneSatisfy(r -> assertThat(r.statusCode()).isEqualTo(500));
        assertThat(refundRows(paymentId)).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM idempotency_records WHERE idempotency_key = ?", key)).isEqualTo(1);
    }

    @Test
    void invalidIdempotencyKeyIsRejectedBeforeAnyRefund() throws Exception {
        SeededOrder order = seedOrder("CONFIRMED", "PAID");
        UUID paymentId = seedCapturedPayment(order);
        String token = loginAsStaff(Set.of("HEAD_OFFICE_ADMIN"), Set.of());

        HttpResponse<String> response = postRefund(token, paymentId, "bad;key", "1000");

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).contains("IDEMPOTENCY_KEY_INVALID");
        assertThat(refundRows(paymentId)).isZero();
    }

    private long refundRows(UUID paymentId) {
        return count("SELECT count(*) FROM payment_refunds WHERE payment_id = ?", paymentId);
    }
}
