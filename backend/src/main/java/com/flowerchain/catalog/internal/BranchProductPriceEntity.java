package com.flowerchain.catalog.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "branch_product_prices")
public class BranchProductPriceEntity {

    @Id
    private UUID id;

    @Column(name = "branch_id", nullable = false)
    private UUID branchId;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "unit_price", nullable = false, precision = 19, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode;

    @Column(name = "is_sellable", nullable = false)
    private boolean sellable;

    @Column(name = "updated_by_staff_id")
    private UUID updatedByStaffId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected BranchProductPriceEntity() {}

    public BranchProductPriceEntity(
            UUID id,
            UUID branchId,
            UUID productId,
            BigDecimal unitPrice,
            String currencyCode,
            boolean sellable,
            UUID updatedByStaffId,
            Instant now) {
        this.id = id;
        this.branchId = branchId;
        this.productId = productId;
        this.unitPrice = unitPrice;
        this.currencyCode = currencyCode;
        this.sellable = sellable;
        this.updatedByStaffId = updatedByStaffId;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void updatePrice(BigDecimal unitPrice, boolean sellable, UUID updatedByStaffId, Instant now) {
        this.unitPrice = unitPrice;
        this.sellable = sellable;
        this.updatedByStaffId = updatedByStaffId;
        this.updatedAt = now;
    }

    public UUID getId() { return id; }
    public UUID getBranchId() { return branchId; }
    public UUID getProductId() { return productId; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public String getCurrencyCode() { return currencyCode; }
    public boolean isSellable() { return sellable; }
    public Instant getUpdatedAt() { return updatedAt; }
}
