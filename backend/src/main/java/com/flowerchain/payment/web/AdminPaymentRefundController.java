package com.flowerchain.payment.web;

import com.flowerchain.payment.application.PaymentRefundService;
import com.flowerchain.payment.application.RefundResponse;
import com.flowerchain.shared.api.BranchAuthorizationGuard;
import com.flowerchain.shared.api.FlowerChainPrincipal;
import com.flowerchain.shared.api.IdempotencyExecutor;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Records refunds of captured payments (FR-027). Branch scope is enforced in {@link PaymentRefundService}. */
@RestController
public class AdminPaymentRefundController {

    static final String ENDPOINT_PATTERN = "POST /api/v1/admin/payments/{paymentId}/refunds";

    private final PaymentRefundService refundService;
    private final BranchAuthorizationGuard branchGuard;
    private final IdempotencyExecutor idempotencyExecutor;

    public AdminPaymentRefundController(
            PaymentRefundService refundService,
            BranchAuthorizationGuard branchGuard,
            IdempotencyExecutor idempotencyExecutor) {
        this.refundService = refundService;
        this.branchGuard = branchGuard;
        this.idempotencyExecutor = idempotencyExecutor;
    }

    public record CreateRefundRequest(
            @NotBlank @Pattern(regexp = "^[0-9]{1,17}(\\.[0-9]{1,2})?$") String refundAmount,
            @NotBlank @Size(min = 5, max = 500) String reason,
            @Size(max = 128) String externalRefundRef) {}

    @PostMapping("/api/v1/admin/payments/{paymentId}/refunds")
    @ResponseStatus(HttpStatus.CREATED)
    public RefundResponse createPaymentRefund(
            @PathVariable("paymentId") UUID paymentId,
            @RequestHeader(name = "Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CreateRefundRequest request) {
        FlowerChainPrincipal staff =
                branchGuard.requireAnyRole(PaymentRefundService.REFUND_ROLES.toArray(String[]::new));
        return idempotencyExecutor.executeIdempotently(
                idempotencyKey,
                "STAFF:" + staff.actorId(),
                ENDPOINT_PATTERN,
                payloadSignature(paymentId, request),
                RefundResponse.class,
                () -> refundService.createRefund(
                        staff,
                        paymentId,
                        new BigDecimal(request.refundAmount()),
                        request.reason(),
                        request.externalRefundRef()));
    }

    /** Length-prefixed so that different field splits can never produce the same signature. */
    static String payloadSignature(UUID paymentId, CreateRefundRequest request) {
        return paymentId + "|" + lengthPrefixed(request.refundAmount()) + "|" + lengthPrefixed(request.reason())
                + "|" + lengthPrefixed(request.externalRefundRef());
    }

    private static String lengthPrefixed(String value) {
        return value == null ? "-1:" : value.length() + ":" + value;
    }
}
