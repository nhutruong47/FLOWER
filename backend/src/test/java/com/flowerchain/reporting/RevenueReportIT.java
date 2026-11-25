package com.flowerchain.reporting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.flowerchain.reporting.application.RevenueReportService;
import com.flowerchain.reporting.application.RevenueReportService.BranchRevenueDto;
import com.flowerchain.reporting.application.RevenueReportService.RevenueReportResponse;
import com.flowerchain.shared.api.FlowerChainException;
import com.flowerchain.support.PostgresIntegrationTestSupport;
import com.flowerchain.support.TestData;
import com.flowerchain.support.TestPrincipals;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;

/**
 * FR-034 against PostgreSQL: totals from order-line snapshots (REQ-006) per processing branch (REQ-005), only
 * COMPLETED orders, half-open period {@code [from, to)}, and branch scope from the principal (REQ-011, REQ-012).
 * Orders are inserted with fixed timestamps in a period no other test uses. CI only (Docker).
 */
class RevenueReportIT extends PostgresIntegrationTestSupport {

    private static final Instant FROM = Instant.parse("2020-03-01T00:00:00Z");
    private static final Instant TO = Instant.parse("2020-04-01T00:00:00Z");

    @Autowired private RevenueReportService revenueReportService;

    private UUID branchA;
    private UUID branchB;

    @BeforeEach
    void seedOrders() {
        branchA = data.branch("Revenue Alpha");
        branchB = data.branch("Revenue Beta");
        UUID product = data.product(TestData.unique("SKU"), "Hydrangea");
        UUID customer = data.customer();

        // Branch A: two completed orders count; delivery fee is not revenue.
        data.order(customer, branchA, product, "DELIVERY", "COMPLETED", "200000.00", 2, "35000.00", FROM);
        data.order(customer, branchA, product, "PICKUP", "COMPLETED", "150000.00", 1, "0.00", FROM.plusSeconds(86_400));
        // Branch A: not counted (status, or outside [from, to)).
        data.order(customer, branchA, product, "PICKUP", "CANCELLED", "999000.00", 1, "0.00", FROM.plusSeconds(3_600));
        data.order(customer, branchA, product, "DELIVERY", "PREPARING", "888000.00", 1, "35000.00", FROM.plusSeconds(7_200));
        data.order(customer, branchA, product, "PICKUP", "COMPLETED", "777000.00", 1, "0.00", TO);
        data.order(customer, branchA, product, "PICKUP", "COMPLETED", "666000.00", 1, "0.00", FROM.minusSeconds(1));
        // Branch B: one completed order.
        data.order(customer, branchB, product, "PICKUP", "COMPLETED", "300000.00", 3, "0.00", TO.minusSeconds(1));
    }

    @Test
    void headOfficeReportAttributesSnapshotRevenueToEachProcessingBranch() {
        TestPrincipals.authenticate(TestPrincipals.staff("HEAD_OFFICE_ADMIN"));

        RevenueReportResponse report = revenueReportService.generateRevenueReport(null, FROM, TO);

        assertThat(report.branches()).filteredOn(row -> row.branchId().equals(branchA)).containsExactly(
                new BranchRevenueDto(branchA, "Revenue Alpha", 2, 1, 1, "550000.00"));
        assertThat(report.branches()).filteredOn(row -> row.branchId().equals(branchB)).containsExactly(
                new BranchRevenueDto(branchB, "Revenue Beta", 1, 0, 1, "900000.00"));
        assertThat(report.currencyCode()).isEqualTo("VND");
    }

    @Test
    void branchManagerOnlySeesTheirOwnBranch() {
        TestPrincipals.authenticate(TestPrincipals.staff("BRANCH_MANAGER", branchA));

        RevenueReportResponse report = revenueReportService.generateRevenueReport(null, FROM, TO);

        assertThat(report.branches()).containsExactly(new BranchRevenueDto(branchA, "Revenue Alpha", 2, 1, 1, "550000.00"));
        assertThat(report.totalChainRevenue()).isEqualTo("550000.00");
        assertThat(report.totalChainOrders()).isEqualTo(2);
    }

    @Test
    void branchManagerAskingForAnotherBranchIsDenied() {
        TestPrincipals.authenticate(TestPrincipals.staff("BRANCH_MANAGER", branchA));

        assertThatThrownBy(() -> revenueReportService.generateRevenueReport(branchB, FROM, TO))
                .isInstanceOfSatisfying(FlowerChainException.class,
                        ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
    }
}
