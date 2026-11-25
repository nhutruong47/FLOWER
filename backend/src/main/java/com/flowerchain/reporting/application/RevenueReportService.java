package com.flowerchain.reporting.application;

import com.flowerchain.branch.api.BranchApi;
import com.flowerchain.branch.api.BranchDto;
import com.flowerchain.order.api.OrderApi;
import com.flowerchain.order.api.OrderApi.OrderDetailDto;
import com.flowerchain.order.api.OrderApi.OrderLineSnapshotDto;
import com.flowerchain.shared.api.BranchAuthorizationGuard;
import com.flowerchain.shared.api.FlowerChainException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * Revenue and order summary per processing branch (FR-034). Read-only: it uses only the public APIs of the
 * {@code order} and {@code branch} modules and never reads live catalog prices.
 *
 * <p>Counting rules (conservative reading of FR-034, see the open questions in TASK-018):
 * <ul>
 *   <li>Only orders in status {@code COMPLETED} count, for revenue and for every order count.</li>
 *   <li>An order belongs to the period when its {@code createdAt} lies in {@code [from, to)}.</li>
 *   <li>An order is attributed to its {@code processingBranchId} (REQ-005).</li>
 *   <li>Gross revenue is the sum of the order-line snapshots ({@code lineTotalSnapshot}, REQ-006). Delivery fees,
 *       discounts and refunds are not included.</li>
 * </ul>
 */
@Service
public class RevenueReportService {

    static final Set<String> REVENUE_ORDER_STATUSES = Set.of("COMPLETED");
    static final String REPORT_CURRENCY = "VND";
    private static final String[] REPORT_ROLES = {"SYSTEM_OWNER", "HEAD_OFFICE_ADMIN", "BRANCH_MANAGER"};

    private final OrderApi orderApi;
    private final BranchApi branchApi;
    private final BranchAuthorizationGuard branchGuard;

    public RevenueReportService(OrderApi orderApi, BranchApi branchApi, BranchAuthorizationGuard branchGuard) {
        this.orderApi = orderApi;
        this.branchApi = branchApi;
        this.branchGuard = branchGuard;
    }

    public record BranchRevenueDto(
            UUID branchId,
            String branchName,
            long completedOrdersCount,
            long deliveryOrdersCount,
            long pickupOrdersCount,
            String grossRevenue) {}

    public record RevenueReportResponse(
            Instant from,
            Instant to,
            String currencyCode,
            String totalChainRevenue,
            long totalChainOrders,
            List<BranchRevenueDto> branches) {}

    /**
     * Builds the report for the authenticated staff user.
     *
     * @param requestedBranchId optional branch filter sent by the client; it is checked against the caller's
     *     server-side branch scope and never trusted on its own (REQ-011, REQ-012)
     * @param from inclusive start of the period (UTC)
     * @param to exclusive end of the period (UTC); must be after {@code from}
     */
    public RevenueReportResponse generateRevenueReport(UUID requestedBranchId, Instant from, Instant to) {
        branchGuard.requireAnyRole(REPORT_ROLES);
        if (from == null || to == null || !from.isBefore(to)) {
            throw FlowerChainException.badRequest("INVALID_DATE_RANGE", "'from' must be earlier than 'to'.");
        }

        Map<UUID, BranchDto> activeBranches = branchApi.listActiveBranches().stream()
                .collect(Collectors.toMap(BranchDto::id, Function.identity(), (a, b) -> a, LinkedHashMap::new));
        Set<UUID> scope = branchGuard.resolveEffectiveBranchFilter(
                Optional.ofNullable(requestedBranchId), activeBranches.keySet());

        Map<UUID, BranchTotals> totalsByBranch = new LinkedHashMap<>();
        for (UUID branchId : scope) {
            BranchDto branch = activeBranches.get(branchId);
            if (branch == null) {
                // Inactive branch the caller is still assigned to, or one requested explicitly by Head Office.
                branch = branchApi.getBranchOrThrow(branchId);
            }
            totalsByBranch.put(branchId, new BranchTotals(branch));
        }

        List<OrderDetailDto> orders = scope.isEmpty() ? List.of() : orderApi.findOrdersForRevenueReport(scope, from, to);
        for (OrderDetailDto order : orders) {
            BranchTotals totals = totalsByBranch.get(order.processingBranchId());
            if (totals == null || !REVENUE_ORDER_STATUSES.contains(order.status())) {
                // Never count an order outside the caller's scope, even if the order API returned one.
                continue;
            }
            if (!REPORT_CURRENCY.equals(order.currencyCode())) {
                throw FlowerChainException.conflict(
                        "REPORT_CURRENCY_MISMATCH",
                        "Report currency mismatch",
                        "Order " + order.orderNumber() + " uses currency " + order.currencyCode()
                                + "; the revenue report only supports " + REPORT_CURRENCY + ".");
            }
            totals.add(order);
        }

        List<BranchRevenueDto> branchRows = totalsByBranch.values().stream()
                .sorted(Comparator.comparing((BranchTotals t) -> t.branchName)
                        .thenComparing(t -> t.branchId))
                .map(BranchTotals::toDto)
                .toList();
        BigDecimal chainRevenue = totalsByBranch.values().stream()
                .map(t -> t.grossRevenue)
                .reduce(zero(), BigDecimal::add);
        long chainOrders = totalsByBranch.values().stream().mapToLong(t -> t.completedOrders).sum();

        return new RevenueReportResponse(
                from,
                to,
                REPORT_CURRENCY,
                chainRevenue.setScale(2, RoundingMode.HALF_UP).toPlainString(),
                chainOrders,
                branchRows);
    }

    private static BigDecimal zero() {
        return BigDecimal.ZERO.setScale(2, RoundingMode.UNNECESSARY);
    }

    private static final class BranchTotals {

        private final UUID branchId;
        private final String branchName;
        private long completedOrders;
        private long deliveryOrders;
        private long pickupOrders;
        private BigDecimal grossRevenue = zero();

        private BranchTotals(BranchDto branch) {
            this.branchId = branch.id();
            this.branchName = branch.name();
        }

        private void add(OrderDetailDto order) {
            completedOrders++;
            if ("DELIVERY".equals(order.fulfilmentType())) {
                deliveryOrders++;
            } else if ("PICKUP".equals(order.fulfilmentType())) {
                pickupOrders++;
            }
            for (OrderLineSnapshotDto line : order.lines()) {
                grossRevenue = grossRevenue.add(new BigDecimal(line.lineTotalSnapshot()));
            }
        }

        private BranchRevenueDto toDto() {
            return new BranchRevenueDto(
                    branchId,
                    branchName,
                    completedOrders,
                    deliveryOrders,
                    pickupOrders,
                    grossRevenue.setScale(2, RoundingMode.HALF_UP).toPlainString());
        }
    }
}
