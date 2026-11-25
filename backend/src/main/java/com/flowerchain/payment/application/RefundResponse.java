package com.flowerchain.payment.application;

import java.time.Instant;
import java.util.UUID;

/** A recorded refund (OpenAPI schema {@code RefundResponse}). Money amounts are decimal strings. */
public record RefundResponse(
        UUID id,
        UUID paymentId,
        UUID orderId,
        String refundAmount,
        String reason,
        String externalRefundRef,
        UUID approvedByStaffId,
        Instant refundedAt) {}
