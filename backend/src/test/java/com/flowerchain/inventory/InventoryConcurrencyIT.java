package com.flowerchain.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import com.flowerchain.inventory.api.InventoryApi;
import com.flowerchain.order.application.OrderService;
import com.flowerchain.order.application.OrderService.OrderItemInputDto;
import com.flowerchain.shared.api.FlowerChainException;
import com.flowerchain.support.PostgresIntegrationTestSupport;
import com.flowerchain.support.TestData;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;

/**
 * TEST-008 (REQ-007): a branch holds the last unit of a product and ten requests race for it. Exactly one wins,
 * the other nine fail cleanly with 409 {@code INVENTORY_INSUFFICIENT_STOCK}, and stock never goes below zero.
 * Runs against PostgreSQL row locks ({@code SELECT ... FOR UPDATE}); CI only (Docker).
 */
class InventoryConcurrencyIT extends PostgresIntegrationTestSupport {

    private static final int CONCURRENT_REQUESTS = 10;

    @Autowired private InventoryApi inventoryApi;
    @Autowired private OrderService orderService;

    @Test
    void tenConcurrentReservationsForTheLastUnitLetExactlyOneSucceed() throws Exception {
        UUID branch = data.branch("Concurrency Branch");
        UUID product = data.product(TestData.unique("SKU"), "Last Rose");
        data.stock(branch, product, 1);

        List<Outcome> outcomes = race(() -> {
            inventoryApi.reserveStockForOrder(
                    branch, UUID.randomUUID(), Map.of(product, 1), UUID.randomUUID(), "CUSTOMER", "concurrency test");
            return null;
        });

        assertExactlyOneSuccessAndCleanConflicts(outcomes);
        assertThat(data.availableStock(branch, product)).isZero();
        assertThat(data.reservedStock(branch, product)).isEqualTo(1);
        assertMovementsNeverNegativeAndSingleReservation(branch, product);
    }

    @Test
    void tenConcurrentPickupOrdersForTheLastUnitCreateExactlyOneOrder() throws Exception {
        UUID branch = data.branch("Concurrency Order Branch");
        UUID product = data.product(TestData.unique("SKU"), "Last Tulip");
        data.branchPrice(branch, product, "150000.00");
        data.stock(branch, product, 1);
        UUID customer = data.customer();

        List<Outcome> outcomes = race(() -> orderService.placeOrder(
                customer, "PICKUP", branch, "PAY_AT_PICKUP", "Nguyen Van A", "0901234567",
                null, null, null, null, null, null, null, null,
                List.of(new OrderItemInputDto(product, 1))));

        assertExactlyOneSuccessAndCleanConflicts(outcomes);
        assertThat(data.availableStock(branch, product)).isZero();
        assertThat(data.reservedStock(branch, product)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM orders WHERE processing_branch_id = ?", Integer.class, branch))
                .as("orders persisted for the branch (failed attempts are rolled back)")
                .isEqualTo(1);
        assertMovementsNeverNegativeAndSingleReservation(branch, product);
    }

    private record Outcome(boolean success, Throwable failure) {}

    /** Runs the same action from {@value #CONCURRENT_REQUESTS} threads released at the same moment. */
    private List<Outcome> race(Callable<Object> action) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(CONCURRENT_REQUESTS);
        CountDownLatch ready = new CountDownLatch(CONCURRENT_REQUESTS);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Outcome>> futures = new ArrayList<>();
            for (int i = 0; i < CONCURRENT_REQUESTS; i++) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    start.await();
                    try {
                        action.call();
                        return new Outcome(true, null);
                    } catch (Exception e) {
                        return new Outcome(false, e);
                    }
                }));
            }
            assertThat(ready.await(30, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            List<Outcome> outcomes = new ArrayList<>();
            for (Future<Outcome> future : futures) {
                outcomes.add(future.get(60, TimeUnit.SECONDS));
            }
            return outcomes;
        } finally {
            pool.shutdownNow();
        }
    }

    private static void assertExactlyOneSuccessAndCleanConflicts(List<Outcome> outcomes) {
        assertThat(outcomes).hasSize(CONCURRENT_REQUESTS);
        assertThat(outcomes).filteredOn(Outcome::success).as("successful requests").hasSize(1);
        assertThat(outcomes).filteredOn(o -> !o.success())
                .as("failed requests")
                .hasSize(CONCURRENT_REQUESTS - 1)
                .allSatisfy(o -> assertThat(o.failure())
                        .isInstanceOfSatisfying(FlowerChainException.class, ex -> {
                            assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                            assertThat(ex.getErrorCode()).isEqualTo("INVENTORY_INSUFFICIENT_STOCK");
                        }));
    }

    private void assertMovementsNeverNegativeAndSingleReservation(UUID branch, UUID product) {
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM inventory_movements WHERE branch_id = ? AND product_id = ? "
                                + "AND reason_code = 'ORDER_RESERVED'",
                        Integer.class, branch, product))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject(
                        "SELECT min(available_after) FROM inventory_movements WHERE branch_id = ? AND product_id = ?",
                        Integer.class, branch, product))
                .isGreaterThanOrEqualTo(0);
    }
}
