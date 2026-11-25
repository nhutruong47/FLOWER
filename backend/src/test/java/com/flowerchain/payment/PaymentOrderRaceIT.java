package com.flowerchain.payment;

import static com.flowerchain.payment.WebhookTestSignatures.payload;
import static org.assertj.core.api.Assertions.assertThat;

import com.flowerchain.order.api.OrderApi.OrderDetailDto;
import com.flowerchain.order.application.OrderService;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Security review F1: a verified capture racing another order writer must leave a consistent order (row locks plus
 * the {@code version} column). Each race is repeated on fresh orders to hit both interleavings. CI only (DEC-015).
 */
class PaymentOrderRaceIT extends PaymentItSupport {

    private static final int ROUNDS = 8;

    @Autowired
    private OrderService orderService;

    @Test
    void captureRacingCustomerCancelNeverLeavesACapturedPaymentOnAnUnpaidOrder() throws Exception {
        for (int round = 0; round < ROUNDS; round++) {
            SeededOrder order = seedPendingOrder();
            byte[] body = payload("evt_cancel_race_" + shortId(), "txn_cancel_race_" + shortId(),
                    order.orderId(), "CAPTURED", "500000.00", "VND");

            List<Object> results = runConcurrently(2, i -> i == 0
                    ? postSignedWebhook(body)
                    : orderService.cancelCustomerOrder(order.customerId(), order.orderId(), "Race test cancel"));

            HttpResponse<String> webhook = webhookResponse(results.get(0));
            assertThat(webhook.statusCode()).isEqualTo(200);
            String outcome = outcome(webhook);
            assertThat(outcome).isIn("PROCESSED", "IGNORED_STALE");
            assertThat(results.get(1)).as("customer cancel result").isInstanceOf(OrderDetailDto.class);

            Map<String, Object> row = orderRow(order.orderId());
            long capturedPayments = capturedPayments(order.orderId());
            assertThat(row.get("status")).isEqualTo("CANCELLED");
            if ("PROCESSED".equals(outcome)) {
                // Capture committed first: the cancel saw it, so the order stays PAID (refund follows manually).
                assertThat(capturedPayments).isEqualTo(1);
                assertThat(row.get("payment_status")).isEqualTo("PAID");
            } else {
                // Cancel committed first: the capture was stale and recorded no payment.
                assertThat(capturedPayments).isZero();
                assertThat(row.get("payment_status")).isEqualTo("UNPAID");
            }
            // The invariant from the review: never CANCELLED + UNPAID together with a CAPTURED payment.
            assertThat(!"UNPAID".equals(row.get("payment_status")) || capturedPayments == 0).isTrue();
        }
    }

    @Test
    void captureRacingReassignPaysTheOrderExactlyOnceAndKeepsItPaid() throws Exception {
        for (int round = 0; round < ROUNDS; round++) {
            SeededOrder order = seedPendingOrder();
            UUID newBranchId = seedBranch();
            byte[] body = payload("evt_reassign_race_" + shortId(), "txn_reassign_race_" + shortId(),
                    order.orderId(), "CAPTURED", "500000.00", "VND");

            List<Object> results = runConcurrently(2, i -> i == 0
                    ? postSignedWebhook(body)
                    : orderService.reassignOrderBranch(
                            order.orderId(), newBranchId, "Race test reassign", UUID.randomUUID()));

            HttpResponse<String> webhook = webhookResponse(results.get(0));
            assertThat(webhook.statusCode()).isEqualTo(200);
            assertThat(outcome(webhook)).isEqualTo("PROCESSED");
            assertThat(results.get(1)).as("reassign result").isInstanceOf(OrderDetailDto.class);

            Map<String, Object> row = orderRow(order.orderId());
            assertThat(row.get("status")).isEqualTo("CONFIRMED");
            assertThat(row.get("payment_status")).isEqualTo("PAID");
            assertThat(row.get("processing_branch_id")).isEqualTo(newBranchId);
            assertThat(capturedPayments(order.orderId())).isEqualTo(1);
            assertThat(count("SELECT count(*) FROM order_status_history WHERE order_id = ? AND to_status = 'CONFIRMED' "
                            + "AND from_status = 'PENDING_PAYMENT'", order.orderId()))
                    .isEqualTo(1);

            // The order can never drop back to PENDING_PAYMENT, so a second capture cannot pay it again.
            HttpResponse<String> secondCapture = postSignedWebhook(payload("evt_second_" + shortId(),
                    "txn_second_" + shortId(), order.orderId(), "CAPTURED", "500000.00", "VND"));
            assertThat(outcome(secondCapture)).isEqualTo("IGNORED_STALE");
            assertThat(capturedPayments(order.orderId())).isEqualTo(1);
        }
    }

    private long capturedPayments(UUID orderId) {
        return count("SELECT count(*) FROM payments WHERE order_id = ? AND status = 'CAPTURED'", orderId);
    }

    @SuppressWarnings("unchecked")
    private static HttpResponse<String> webhookResponse(Object result) {
        assertThat(result).as("webhook result").isInstanceOf(HttpResponse.class);
        return (HttpResponse<String>) result;
    }
}
