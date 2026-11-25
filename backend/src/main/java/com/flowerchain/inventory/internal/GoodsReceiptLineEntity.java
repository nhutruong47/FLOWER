package com.flowerchain.inventory.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "goods_receipt_lines")
public class GoodsReceiptLineEntity {

    @Id
    private UUID id;

    @Column(name = "goods_receipt_id", nullable = false)
    private UUID goodsReceiptId;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "received_quantity", nullable = false)
    private int receivedQuantity;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected GoodsReceiptLineEntity() {}

    public GoodsReceiptLineEntity(
            UUID id,
            UUID goodsReceiptId,
            UUID productId,
            int receivedQuantity,
            Instant now) {
        this.id = id;
        this.goodsReceiptId = goodsReceiptId;
        this.productId = productId;
        this.receivedQuantity = receivedQuantity;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public UUID getGoodsReceiptId() {
        return goodsReceiptId;
    }

    public UUID getProductId() {
        return productId;
    }

    public int getReceivedQuantity() {
        return receivedQuantity;
    }
}
