package com.flowerchain.order;

import static org.assertj.core.api.Assertions.assertThat;

import com.flowerchain.catalog.application.CatalogService;
import com.flowerchain.order.api.OrderApi;
import com.flowerchain.order.api.OrderApi.OrderDetailDto;
import com.flowerchain.order.api.OrderApi.OrderLineSnapshotDto;
import com.flowerchain.order.application.OrderService;
import com.flowerchain.order.application.OrderService.OrderItemInputDto;
import com.flowerchain.support.PostgresIntegrationTestSupport;
import com.flowerchain.support.TestData;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * TEST-007 (REQ-006): after an order is placed, changing the branch price, the product name and the SKU does not
 * change the order. Lines keep the SKU, name and unit price from order time. CI only (Docker).
 */
class OrderSnapshotIT extends PostgresIntegrationTestSupport {

    @Autowired private OrderService orderService;
    @Autowired private OrderApi orderApi;
    @Autowired private CatalogService catalogService;

    @Test
    void orderLinesKeepSkuNameAndUnitPriceAfterTheCatalogAndBranchPriceChange() {
        UUID branch = data.branch("Snapshot Branch");
        String originalSku = TestData.unique("ROSE-RED");
        UUID product = data.product(originalSku, "12 Red Roses");
        data.branchPrice(branch, product, "250000.00");
        data.stock(branch, product, 5);
        UUID customer = data.customer();

        OrderDetailDto placed = orderService.placeOrder(
                customer, "PICKUP", branch, "PAY_AT_PICKUP", "Nguyen Van A", "0901234567",
                null, null, null, null, null, null, null, "500000.00",
                List.of(new OrderItemInputDto(product, 2)));
        assertThat(placed.orderNumber()).matches("ORD-\\d+");

        // Head office changes the branch price, renames the product and changes its SKU.
        catalogService.upsertBranchPrice(branch, product, new BigDecimal("300000.00"), true, null);
        catalogService.updateProductDetails(product, "12 Premium Red Roses", null, "ACTIVE");
        jdbc.update("UPDATE products SET sku = ? WHERE id = ?", TestData.unique("ROSE-PREMIUM"), product);

        OrderDetailDto reread = orderService.getCustomerOrderOrThrow404(customer, placed.id());
        assertThat(reread.lines()).singleElement().satisfies(line -> {
            assertThat(line.skuSnapshot()).isEqualTo(originalSku);
            assertThat(line.productNameSnapshot()).isEqualTo("12 Red Roses");
            assertThat(line.unitPriceSnapshot()).isEqualTo("250000.00");
            assertThat(line.quantity()).isEqualTo(2);
            assertThat(line.lineTotalSnapshot()).isEqualTo("500000.00");
        });
        assertThat(reread.itemsSubtotal()).isEqualTo("500000.00");
        assertThat(reread.totalAmount()).isEqualTo("500000.00");

        Map<String, Object> storedLine = jdbc.queryForMap(
                "SELECT sku_snapshot, product_name_snapshot, unit_price_snapshot FROM order_lines WHERE order_id = ?",
                placed.id());
        assertThat(storedLine.get("sku_snapshot")).isEqualTo(originalSku);
        assertThat(storedLine.get("product_name_snapshot")).isEqualTo("12 Red Roses");
        assertThat((BigDecimal) storedLine.get("unit_price_snapshot")).isEqualByComparingTo("250000.00");

        // The revenue report path reads the same snapshots.
        Instant now = Instant.now();
        List<OrderDetailDto> reportOrders =
                orderApi.findOrdersForRevenueReport(Set.of(branch), now.minusSeconds(3600), now.plusSeconds(3600));
        assertThat(reportOrders).singleElement()
                .satisfies(o -> assertThat(o.lines()).extracting(OrderLineSnapshotDto::unitPriceSnapshot)
                        .containsExactly("250000.00"));

        // Control: a new quote does see the new price, so the change really happened.
        assertThat(orderService.quoteOrder(null, "PICKUP", branch, null, null, List.of(new OrderItemInputDto(product, 2)))
                        .itemsSubtotal())
                .isEqualTo("600000.00");
    }
}
