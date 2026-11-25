package com.flowerchain.order.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "order_lines")
public class OrderLineEntity {

    @Id
    private UUID id;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "sku_snapshot", nullable = false, length = 64)
    private String skuSnapshot;

    @Column(name = "product_name_snapshot", nullable = false, length = 200)
    private String productNameSnapshot;

    @Column(name = "unit_price_snapshot", nullable = false, precision = 19, scale = 2)
    private BigDecimal unitPriceSnapshot;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    @Column(name = "line_total_snapshot", nullable = false, precision = 19, scale = 2)
    private BigDecimal lineTotalSnapshot;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected OrderLineEntity() {}

    public OrderLineEntity(
            UUID id,
            UUID orderId,
            UUID productId,
            String skuSnapshot,
            String productNameSnapshot,
            BigDecimal unitPriceSnapshot,
            int quantity,
            BigDecimal lineTotalSnapshot,
            Instant now) {
        this.id = id;
        this.orderId = orderId;
        this.productId = productId;
        this.skuSnapshot = skuSnapshot;
        this.productNameSnapshot = productNameSnapshot;
        this.unitPriceSnapshot = unitPriceSnapshot;
        this.quantity = quantity;
        this.lineTotalSnapshot = lineTotalSnapshot;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public UUID getProductId() {
        return productId;
    }

    public String getSkuSnapshot() {
        return skuSnapshot;
    }

    public String getProductNameSnapshot() {
        return productNameSnapshot;
    }

    public BigDecimal getUnitPriceSnapshot() {
        return unitPriceSnapshot;
    }

    public int getQuantity() {
        return quantity;
    }

    public BigDecimal getLineTotalSnapshot() {
        return lineTotalSnapshot;
    }
}
