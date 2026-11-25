package com.flowerchain.reporting.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.flowerchain.branch.api.BranchApi;
import com.flowerchain.branch.api.BranchDto;
import com.flowerchain.order.api.OrderApi;
import com.flowerchain.order.api.OrderApi.OrderDetailDto;
import com.flowerchain.order.api.OrderApi.OrderLineSnapshotDto;
import com.flowerchain.reporting.application.RevenueReportService.BranchRevenueDto;
import com.flowerchain.reporting.application.RevenueReportService.RevenueReportResponse;
import com.flowerchain.shared.api.BranchAuthorizationGuard;
import com.flowerchain.shared.api.FlowerChainException;
import com.flowerchain.support.TestPrincipals;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

/**
 * Revenue report (FR-034): totals come from order-line snapshots (REQ-006), are attributed to the processing branch
 * (REQ-005), only count COMPLETED orders, and are limited to the caller's server-side branch scope (REQ-011,
 * REQ-012). The real {@link BranchAuthorizationGuard} is used with a principal in the security context.
 */
@ExtendWith(MockitoExtension.class)
class RevenueReportServiceTest {

    static final UUID BRANCH_A = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    static final UUID BRANCH_B = UUID.fromString("00000000-0000-0000-0000-00000000000b");
    static final UUID BRANCH_C = UUID.fromString("00000000-0000-0000-0000-00000000000c");
    static final Instant FROM = Instant.parse("2026-11-01T00:00:00Z");
    static final Instant TO = Instant.parse("2026-12-01T00:00:00Z");

    @Mock private OrderApi orderApi;
    @Mock private BranchApi branchApi;

    private RevenueReportService service;

    @BeforeEach
    void setUp() {
        service = new RevenueReportService(orderApi, branchApi, new BranchAuthorizationGuard());
    }

    @AfterEach
    void clearSecurityContext() {
        TestPrincipals.clear();
    }

    static BranchDto branch(UUID id, String name, String status) {
        return new BranchDto(id, "BR-" + name, name, "1 Flower St", "District 1", "HCMC", "0280000000",
                new BigDecimal("10.776900"), new BigDecimal("106.700900"), new BigDecimal("15.00"),
                "08:00-20:00", status, FROM, FROM);
    }

    /** An order whose header total deliberately differs from its line snapshots (it includes the delivery fee). */
    static OrderDetailDto order(UUID branchId, String fulfilment, String status, String... lineTotals) {
        List<OrderLineSnapshotDto> lines = Arrays.stream(lineTotals)
                .map(total -> new OrderLineSnapshotDto(UUID.randomUUID(), "SKU", "Bouquet", total, 1, total))
                .toList();
        BigDecimal subtotal = Arrays.stream(lineTotals).map(BigDecimal::new).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal fee = "DELIVERY".equals(fulfilment) ? new BigDecimal("35000.00") : new BigDecimal("0.00");
        return new OrderDetailDto(UUID.randomUUID(), "ORD-" + UUID.randomUUID(), UUID.randomUUID(), branchId,
                fulfilment, status, "PAID", subtotal.toPlainString(), fee.toPlainString(), "0.00",
                subtotal.add(fee).toPlainString(), "VND", "Recipient", "0901234567", null, null, null, lines,
                FROM.plusSeconds(3600), FROM.plusSeconds(3600));
    }

    @Test
    void headOfficeSeesEveryActiveBranchWithRevenueFromLineSnapshotsOfCompletedOrdersOnly() {
        TestPrincipals.authenticate(TestPrincipals.staff("HEAD_OFFICE_ADMIN"));
        when(branchApi.listActiveBranches())
                .thenReturn(List.of(branch(BRANCH_B, "Beta", "ACTIVE"), branch(BRANCH_A, "Alpha", "ACTIVE")));
        when(orderApi.findOrdersForRevenueReport(Set.of(BRANCH_A, BRANCH_B), FROM, TO)).thenReturn(List.of(
                order(BRANCH_A, "DELIVERY", "COMPLETED", "500000.00", "100000.00"),
                order(BRANCH_A, "PICKUP", "COMPLETED", "250000.00"),
                order(BRANCH_A, "DELIVERY", "CANCELLED", "999999.00"),
                order(BRANCH_A, "PICKUP", "PENDING_PAYMENT", "123456.00"),
                order(BRANCH_A, "DELIVERY", "OUT_FOR_DELIVERY", "77777.00"),
                order(BRANCH_B, "PICKUP", "COMPLETED", "300000.00")));

        RevenueReportResponse report = service.generateRevenueReport(null, FROM, TO);

        assertThat(report.from()).isEqualTo(FROM);
        assertThat(report.to()).isEqualTo(TO);
        assertThat(report.currencyCode()).isEqualTo("VND");
        assertThat(report.branches()).containsExactly(
                new BranchRevenueDto(BRANCH_A, "Alpha", 2, 1, 1, "850000.00"),
                new BranchRevenueDto(BRANCH_B, "Beta", 1, 0, 1, "300000.00"));
        assertThat(report.totalChainRevenue()).isEqualTo("1150000.00");
        assertThat(report.totalChainOrders()).isEqualTo(3);
    }

    @Test
    void branchManagerWithoutAFilterOnlyGetsTheirOwnBranch() {
        TestPrincipals.authenticate(TestPrincipals.staff("BRANCH_MANAGER", BRANCH_A));
        when(branchApi.listActiveBranches())
                .thenReturn(List.of(branch(BRANCH_A, "Alpha", "ACTIVE"), branch(BRANCH_B, "Beta", "ACTIVE")));
        // Even if the order API returned another branch's order, it must not be counted.
        when(orderApi.findOrdersForRevenueReport(Set.of(BRANCH_A), FROM, TO)).thenReturn(List.of(
                order(BRANCH_A, "PICKUP", "COMPLETED", "250000.00"),
                order(BRANCH_B, "PICKUP", "COMPLETED", "300000.00")));

        RevenueReportResponse report = service.generateRevenueReport(null, FROM, TO);

        assertThat(report.branches()).extracting(BranchRevenueDto::branchId).containsExactly(BRANCH_A);
        assertThat(report.totalChainRevenue()).isEqualTo("250000.00");
        assertThat(report.totalChainOrders()).isEqualTo(1);
    }

    @Test
    void branchManagerAskingForAnotherBranchIsDeniedBeforeAnyOrderIsRead() {
        TestPrincipals.authenticate(TestPrincipals.staff("BRANCH_MANAGER", BRANCH_A));
        when(branchApi.listActiveBranches())
                .thenReturn(List.of(branch(BRANCH_A, "Alpha", "ACTIVE"), branch(BRANCH_B, "Beta", "ACTIVE")));

        assertThatThrownBy(() -> service.generateRevenueReport(BRANCH_B, FROM, TO))
                .isInstanceOfSatisfying(FlowerChainException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(ex.getErrorCode()).isEqualTo("BRANCH_ACCESS_DENIED");
                });
        verifyNoInteractions(orderApi);
    }

    @Test
    void headOfficeCanFilterOnOneBranch() {
        TestPrincipals.authenticate(TestPrincipals.staff("SYSTEM_OWNER"));
        when(branchApi.listActiveBranches())
                .thenReturn(List.of(branch(BRANCH_A, "Alpha", "ACTIVE"), branch(BRANCH_B, "Beta", "ACTIVE")));
        when(orderApi.findOrdersForRevenueReport(Set.of(BRANCH_B), FROM, TO))
                .thenReturn(List.of(order(BRANCH_B, "DELIVERY", "COMPLETED", "410000.00")));

        RevenueReportResponse report = service.generateRevenueReport(BRANCH_B, FROM, TO);

        assertThat(report.branches()).containsExactly(new BranchRevenueDto(BRANCH_B, "Beta", 1, 1, 0, "410000.00"));
        assertThat(report.totalChainRevenue()).isEqualTo("410000.00");
    }

    @Test
    void anInactiveBranchInTheCallersScopeIsStillReportedByName() {
        TestPrincipals.authenticate(TestPrincipals.staff("BRANCH_MANAGER", BRANCH_A, BRANCH_C));
        when(branchApi.listActiveBranches()).thenReturn(List.of(branch(BRANCH_A, "Alpha", "ACTIVE")));
        when(branchApi.getBranchOrThrow(BRANCH_C)).thenReturn(branch(BRANCH_C, "Closed Corner", "INACTIVE"));
        when(orderApi.findOrdersForRevenueReport(Set.of(BRANCH_A, BRANCH_C), FROM, TO))
                .thenReturn(List.of(order(BRANCH_C, "PICKUP", "COMPLETED", "90000.00")));

        RevenueReportResponse report = service.generateRevenueReport(null, FROM, TO);

        assertThat(report.branches()).containsExactly(
                new BranchRevenueDto(BRANCH_A, "Alpha", 0, 0, 0, "0.00"),
                new BranchRevenueDto(BRANCH_C, "Closed Corner", 1, 0, 1, "90000.00"));
    }

    @Test
    void emptyPeriodReportsZeroTotals() {
        TestPrincipals.authenticate(TestPrincipals.staff("BRANCH_MANAGER", BRANCH_A));
        when(branchApi.listActiveBranches()).thenReturn(List.of(branch(BRANCH_A, "Alpha", "ACTIVE")));
        when(orderApi.findOrdersForRevenueReport(Set.of(BRANCH_A), FROM, TO)).thenReturn(List.of());

        RevenueReportResponse report = service.generateRevenueReport(null, FROM, TO);

        assertThat(report.branches()).containsExactly(new BranchRevenueDto(BRANCH_A, "Alpha", 0, 0, 0, "0.00"));
        assertThat(report.totalChainRevenue()).isEqualTo("0.00");
        assertThat(report.totalChainOrders()).isZero();
    }

    @Test
    void fromMustBeEarlierThanTo() {
        TestPrincipals.authenticate(TestPrincipals.staff("HEAD_OFFICE_ADMIN"));

        for (Instant[] range : new Instant[][] {{TO, FROM}, {FROM, FROM}, {null, TO}, {FROM, null}}) {
            assertThatThrownBy(() -> service.generateRevenueReport(null, range[0], range[1]))
                    .isInstanceOfSatisfying(FlowerChainException.class, ex -> {
                        assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                        assertThat(ex.getErrorCode()).isEqualTo("INVALID_DATE_RANGE");
                    });
        }
        verifyNoInteractions(orderApi, branchApi);
    }

    @Test
    void rolesWithoutRevenueAccessAreDenied() {
        for (String role : List.of("SALES_STAFF", "INVENTORY_STAFF", "DELIVERY_STAFF")) {
            TestPrincipals.authenticate(TestPrincipals.staff(role, BRANCH_A));

            assertThatThrownBy(() -> service.generateRevenueReport(BRANCH_A, FROM, TO))
                    .isInstanceOfSatisfying(FlowerChainException.class, ex -> {
                        assertThat(ex.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                        assertThat(ex.getErrorCode()).isEqualTo("AUTH_FORBIDDEN_ROLE");
                    });
        }
        verifyNoInteractions(orderApi, branchApi);
    }

    @Test
    void customersAndAnonymousCallersAreDenied() {
        TestPrincipals.authenticate(TestPrincipals.customer());
        assertThatThrownBy(() -> service.generateRevenueReport(null, FROM, TO))
                .isInstanceOfSatisfying(FlowerChainException.class,
                        ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.FORBIDDEN));

        TestPrincipals.clear();
        assertThatThrownBy(() -> service.generateRevenueReport(null, FROM, TO))
                .isInstanceOfSatisfying(FlowerChainException.class,
                        ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED));
        verifyNoInteractions(orderApi, branchApi);
    }

    @Test
    void ordersInAnotherCurrencyAreNotSilentlyMixedIntoTheTotals() {
        TestPrincipals.authenticate(TestPrincipals.staff("BRANCH_MANAGER", BRANCH_A));
        when(branchApi.listActiveBranches()).thenReturn(List.of(branch(BRANCH_A, "Alpha", "ACTIVE")));
        OrderDetailDto vnd = order(BRANCH_A, "PICKUP", "COMPLETED", "100000.00");
        OrderDetailDto usd = new OrderDetailDto(vnd.id(), vnd.orderNumber(), vnd.customerId(), BRANCH_A, "PICKUP",
                "COMPLETED", "PAID", "10.00", "0.00", "0.00", "10.00", "USD", "R", "0901234567", null, null, null,
                vnd.lines(), vnd.createdAt(), vnd.updatedAt());
        when(orderApi.findOrdersForRevenueReport(Set.of(BRANCH_A), FROM, TO)).thenReturn(List.of(vnd, usd));

        assertThatThrownBy(() -> service.generateRevenueReport(null, FROM, TO))
                .isInstanceOfSatisfying(FlowerChainException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo("REPORT_CURRENCY_MISMATCH"));
        verify(orderApi).findOrdersForRevenueReport(Set.of(BRANCH_A), FROM, TO);
    }
}
