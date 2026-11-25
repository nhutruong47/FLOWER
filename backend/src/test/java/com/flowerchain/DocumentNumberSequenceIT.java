package com.flowerchain;

import static org.assertj.core.api.Assertions.assertThat;

import com.flowerchain.inventory.application.InventoryService;
import com.flowerchain.inventory.application.InventoryService.GoodsReceiptDto;
import com.flowerchain.inventory.application.InventoryService.GoodsReceiptLineInput;
import com.flowerchain.inventory.application.InventoryService.StockTransferDto;
import com.flowerchain.inventory.application.InventoryService.StockTransferLineInput;
import com.flowerchain.order.internal.OrderRepository;
import com.flowerchain.support.PostgresIntegrationTestSupport;
import com.flowerchain.support.TestData;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * TASK-018 defect 1: ORD-, GR- and TRF- numbers come from PostgreSQL sequences created by
 * {@code V202611251000__create_document_number_sequences.sql}, so they keep increasing across application restarts
 * instead of restarting from an in-memory counter. CI only (Docker).
 */
class DocumentNumberSequenceIT extends PostgresIntegrationTestSupport {

    @Autowired private InventoryService inventoryService;
    @Autowired private OrderRepository orderRepository;

    @Test
    void migrationCreatesOneSequencePerDocumentType() {
        List<String> sequences = jdbc.queryForList(
                "SELECT sequencename FROM pg_sequences WHERE schemaname = current_schema() ORDER BY sequencename",
                String.class);

        assertThat(sequences)
                .contains("goods_receipt_number_seq", "order_number_seq", "stock_transfer_number_seq");
    }

    @Test
    void orderNumbersAreDrawnFromTheDatabaseSequence() {
        long first = orderRepository.nextOrderNumber();
        long second = orderRepository.nextOrderNumber();

        assertThat(first).isGreaterThan(2000L);
        assertThat(second).isGreaterThan(first);
        assertThat(lastValue("order_number_seq")).isEqualTo(second);
    }

    @Test
    void goodsReceiptAndTransferNumbersAreDrawnFromTheirOwnSequences() {
        UUID branchA = data.branch("Numbering A");
        UUID branchB = data.branch("Numbering B");
        UUID product = data.product(TestData.unique("SKU"), "Carnation");
        UUID staff = data.staffUser();

        GoodsReceiptDto first = inventoryService.recordGoodsReceipt(
                branchA, "Supplier", null, null, List.of(new GoodsReceiptLineInput(product, 1)), staff);
        GoodsReceiptDto second = inventoryService.recordGoodsReceipt(
                branchA, "Supplier", null, null, List.of(new GoodsReceiptLineInput(product, 1)), staff);
        StockTransferDto transfer = inventoryService.createStockTransfer(
                branchA, branchB, null, List.of(new StockTransferLineInput(product, 1, null)), staff);

        assertThat(first.receiptNumber()).matches("GR-\\d+");
        assertThat(numberOf(second.receiptNumber())).isGreaterThan(numberOf(first.receiptNumber()));
        // The persisted counter lives in the database: a restarted application continues from here.
        assertThat(lastValue("goods_receipt_number_seq")).isGreaterThanOrEqualTo(numberOf(second.receiptNumber()));
        assertThat(transfer.transferNumber()).matches("TRF-\\d+");
        assertThat(lastValue("stock_transfer_number_seq")).isGreaterThanOrEqualTo(numberOf(transfer.transferNumber()));
    }

    private long lastValue(String sequence) {
        return jdbc.queryForObject("SELECT last_value FROM " + sequence, Long.class);
    }

    private static long numberOf(String documentNumber) {
        return Long.parseLong(documentNumber.substring(documentNumber.indexOf('-') + 1));
    }
}
