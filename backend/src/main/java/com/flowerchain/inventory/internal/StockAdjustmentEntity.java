package com.flowerchain.inventory.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "stock_adjustments")
public class StockAdjustmentEntity {

    @Id
    private UUID id;

    @Column(name = "branch_id", nullable = false)
    private UUID branchId;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "adjustment_type", nullable = false, length = 32)
    private String adjustmentType;

    @Column(name = "quantity_delta", nullable = false)
    private int quantityDelta;

    @Column(name = "reason_note", nullable = false, length = 500)
    private String reasonNote;

    @Column(name = "recorded_by_staff_id", nullable = false)
    private UUID recordedByStaffId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected StockAdjustmentEntity() {}

    public StockAdjustmentEntity(
            UUID id,
            UUID branchId,
            UUID productId,
            String adjustmentType,
            int quantityDelta,
            String reasonNote,
            UUID recordedByStaffId,
            Instant now) {
        this.id = id;
        this.branchId = branchId;
        this.productId = productId;
        this.adjustmentType = adjustmentType;
        this.quantityDelta = quantityDelta;
        this.reasonNote = reasonNote;
        this.recordedByStaffId = recordedByStaffId;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public UUID getBranchId() {
        return branchId;
    }

    public UUID getProductId() {
        return productId;
    }

    public String getAdjustmentType() {
        return adjustmentType;
    }

    public int getQuantityDelta() {
        return quantityDelta;
    }

    public String getReasonNote() {
        return reasonNote;
    }

    public UUID getRecordedByStaffId() {
        return recordedByStaffId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
