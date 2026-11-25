package com.flowerchain.inventory.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "stock_transfer_lines")
public class StockTransferLineEntity {

    @Id
    private UUID id;

    @Column(name = "stock_transfer_id", nullable = false)
    private UUID stockTransferId;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "requested_quantity", nullable = false)
    private int requestedQuantity;

    @Column(name = "dispatched_quantity")
    private Integer dispatchedQuantity;

    @Column(name = "received_quantity")
    private Integer receivedQuantity;

    @Column(name = "discrepancy_note", length = 500)
    private String discrepancyNote;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected StockTransferLineEntity() {}

    public StockTransferLineEntity(
            UUID id,
            UUID stockTransferId,
            UUID productId,
            int requestedQuantity,
            Instant now) {
        this.id = id;
        this.stockTransferId = stockTransferId;
        this.productId = productId;
        this.requestedQuantity = requestedQuantity;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void recordDispatch(int dispatchedQty, Instant now) {
        this.dispatchedQuantity = dispatchedQty;
        this.updatedAt = now;
    }

    public void recordReceipt(int receivedQty, String discrepancyNote, Instant now) {
        this.receivedQuantity = receivedQty;
        this.discrepancyNote = discrepancyNote;
        this.updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public UUID getStockTransferId() {
        return stockTransferId;
    }

    public UUID getProductId() {
        return productId;
    }

    public int getRequestedQuantity() {
        return requestedQuantity;
    }

    public Integer getDispatchedQuantity() {
        return dispatchedQuantity;
    }

    public Integer getReceivedQuantity() {
        return receivedQuantity;
    }

    public String getDiscrepancyNote() {
        return discrepancyNote;
    }
}
