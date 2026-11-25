package com.flowerchain.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import com.flowerchain.inventory.api.InventoryApi;
import com.flowerchain.inventory.application.InventoryService;
import com.flowerchain.inventory.application.InventoryService.GoodsReceiptDto;
import com.flowerchain.inventory.application.InventoryService.GoodsReceiptLineInput;
import com.flowerchain.inventory.application.InventoryService.StockAdjustmentDto;
import com.flowerchain.inventory.application.InventoryService.StockTransferDto;
import com.flowerchain.inventory.application.InventoryService.StockTransferLineInput;
import com.flowerchain.support.PostgresIntegrationTestSupport;
import com.flowerchain.support.TestData;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * TEST-010 (REQ-008): goods receipt (+10), order reservation (-2), cancellation release (+2), spoilage (-1) and an
 * inter-branch transfer (-3 at A, +3 at B) each write one {@code inventory_movements} row with branch, reason,
 * quantity delta, after-values, source document, actor and UTC timestamp. CI only (Docker).
 */
class InventoryAuditTrailIT extends PostgresIntegrationTestSupport {

    @Autowired private InventoryService inventoryService;
    @Autowired private InventoryApi inventoryApi;

    @Test
    void everyStockChangeWritesACompleteMovementRow() {
        UUID branchA = data.branch("Audit Branch A");
        UUID branchB = data.branch("Audit Branch B");
        UUID product = data.product(TestData.unique("SKU"), "Sunflower");
        UUID staffA = data.staffUser();
        UUID staffB = data.staffUser();
        UUID customer = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();

        GoodsReceiptDto receipt = inventoryService.recordGoodsReceipt(
                branchA, "Da Lat Farm", "INV-1", null, List.of(new GoodsReceiptLineInput(product, 10)), staffA);
        inventoryApi.reserveStockForOrder(branchA, orderId, Map.of(product, 2), customer, "CUSTOMER", "Reserved");
        inventoryApi.releaseOrderStock(branchA, orderId, Map.of(product, 2), customer, "CUSTOMER", "Cancelled");
        StockAdjustmentDto spoilage =
                inventoryService.recordStockAdjustment(branchA, product, "SPOILAGE", -1, "Wilted", staffA);
        StockTransferDto transfer = inventoryService.createStockTransfer(
                branchA, branchB, "Rebalance", List.of(new StockTransferLineInput(product, 3, null)), staffA);
        inventoryService.dispatchStockTransfer(
                transfer.id(), null, List.of(new StockTransferLineInput(product, 3, null)), staffA, false, Set.of(branchA));
        inventoryService.receiveStockTransfer(
                transfer.id(), null, List.of(new StockTransferLineInput(product, 3, null)), staffB, false, Set.of(branchB));

        assertMovement(branchA, product, "GOODS_RECEIPT", 10, 10, 0, "GOODS_RECEIPT", receipt.id(), staffA, "STAFF");
        assertMovement(branchA, product, "ORDER_RESERVED", -2, 8, 2, "ORDER", orderId, customer, "CUSTOMER");
        assertMovement(branchA, product, "ORDER_RELEASED", 2, 10, 0, "ORDER", orderId, customer, "CUSTOMER");
        assertMovement(branchA, product, "SPOILAGE", -1, 9, 0, "STOCK_ADJUSTMENT", spoilage.id(), staffA, "STAFF");
        assertMovement(branchA, product, "TRANSFER_OUT", -3, 6, 0, "STOCK_TRANSFER", transfer.id(), staffA, "STAFF");
        assertMovement(branchB, product, "TRANSFER_IN", 3, 3, 0, "STOCK_TRANSFER", transfer.id(), staffB, "STAFF");

        assertThat(countMovements(branchA, product)).isEqualTo(5);
        assertThat(countMovements(branchB, product)).isEqualTo(1);
        assertThat(data.availableStock(branchA, product)).isEqualTo(6);
        assertThat(data.availableStock(branchB, product)).isEqualTo(3);
        // For this sequence (no confirmed deductions) the audited deltas add up to the stock balance (FR-035 AC1).
        assertThat(jdbc.queryForObject(
                        "SELECT sum(quantity_delta) FROM inventory_movements WHERE branch_id = ? AND product_id = ?",
                        Integer.class, branchA, product))
                .isEqualTo(6);
    }

    private int countMovements(UUID branchId, UUID productId) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM inventory_movements WHERE branch_id = ? AND product_id = ?",
                Integer.class, branchId, productId);
    }

    private void assertMovement(
            UUID branchId,
            UUID productId,
            String reasonCode,
            int quantityDelta,
            int availableAfter,
            int reservedAfter,
            String sourceDocumentType,
            UUID sourceDocumentId,
            UUID actorId,
            String actorType) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT * FROM inventory_movements WHERE branch_id = ? AND product_id = ? AND reason_code = ?",
                branchId, productId, reasonCode);
        assertThat(rows).as("%s movements at branch %s", reasonCode, branchId).hasSize(1);
        Map<String, Object> row = rows.get(0);
        assertThat(row.get("quantity_delta")).isEqualTo(quantityDelta);
        assertThat(row.get("available_after")).isEqualTo(availableAfter);
        assertThat(row.get("reserved_after")).isEqualTo(reservedAfter);
        assertThat(row.get("source_document_type")).isEqualTo(sourceDocumentType);
        assertThat(row.get("source_document_id")).isEqualTo(sourceDocumentId);
        assertThat(row.get("actor_id")).isEqualTo(actorId);
        assertThat(row.get("actor_type")).isEqualTo(actorType);
        assertThat((String) row.get("note")).isNotBlank();
        assertThat(jdbc.queryForObject(
                        "SELECT created_at FROM inventory_movements WHERE id = ?", OffsetDateTime.class, row.get("id")))
                .isNotNull();
    }
}
