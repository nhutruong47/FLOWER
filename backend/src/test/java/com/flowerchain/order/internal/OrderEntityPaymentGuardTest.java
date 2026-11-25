package com.flowerchain.order.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Only PENDING_PAYMENT + UNPAID may become PAID + CONFIRMED (REQ-010, FR-026 E1). */
class OrderEntityPaymentGuardTest {

    private static final Instant CREATED = Instant.parse("2026-11-25T07:00:00Z");
    private static final Instant NOW = Instant.parse("2026-11-25T08:00:00Z");

    @Test
    void pendingUnpaidOrderBecomesPaidAndConfirmed() {
        OrderEntity order = order("PENDING_PAYMENT", "UNPAID");

        boolean applied = order.markPaidAndConfirmed(NOW);

        assertThat(applied).isTrue();
        assertThat(order.getStatus()).isEqualTo("CONFIRMED");
        assertThat(order.getPaymentStatus()).isEqualTo("PAID");
        assertThat(order.getUpdatedAt()).isEqualTo(NOW);
    }

    @ParameterizedTest
    @CsvSource({
        "CANCELLED, UNPAID",
        "CONFIRMED, PAID",
        "CONFIRMED, UNPAID",
        "PREPARING, PAID",
        "COMPLETED, PAID",
        "PENDING_PAYMENT, PAID",
        "PENDING_PAYMENT, REFUNDED",
        "CANCELLED, REFUNDED",
        "CONFIRMED, PARTIALLY_REFUNDED"
    })
    void anyOtherStateIsLeftUntouched(String status, String paymentStatus) {
        OrderEntity order = order(status, paymentStatus);

        boolean applied = order.markPaidAndConfirmed(NOW);

        assertThat(applied).isFalse();
        assertThat(order.getStatus()).isEqualTo(status);
        assertThat(order.getPaymentStatus()).isEqualTo(paymentStatus);
        assertThat(order.getUpdatedAt()).isEqualTo(CREATED);
    }

    @Test
    void secondApplicationIsIgnored() {
        OrderEntity order = order("PENDING_PAYMENT", "UNPAID");

        assertThat(order.markPaidAndConfirmed(NOW)).isTrue();
        assertThat(order.markPaidAndConfirmed(NOW.plusSeconds(1))).isFalse();
        assertThat(order.getUpdatedAt()).isEqualTo(NOW);
    }

    static OrderEntity order(String status, String paymentStatus) {
        return new OrderEntity(
                UUID.randomUUID(),
                "ORD-2001",
                UUID.randomUUID(),
                UUID.randomUUID(),
                "PICKUP",
                status,
                paymentStatus,
                "VND",
                new BigDecimal("500000.00"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                new BigDecimal("500000.00"),
                "Recipient",
                "0900000000",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                CREATED);
    }
}
