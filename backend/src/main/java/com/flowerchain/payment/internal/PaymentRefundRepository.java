package com.flowerchain.payment.internal;

import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentRefundRepository extends JpaRepository<PaymentRefundEntity, UUID> {

    /** Sum of all refunds recorded for the payment, or {@code null} if there are none. */
    @Query("SELECT SUM(r.refundAmount) FROM PaymentRefundEntity r WHERE r.paymentId = :paymentId")
    BigDecimal sumRefundAmountByPaymentId(@Param("paymentId") UUID paymentId);
}
