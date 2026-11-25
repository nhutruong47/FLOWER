package com.flowerchain.inventory.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "branch_inventories")
public class BranchInventoryEntity {

    @Id
    private UUID id;

    @Column(name = "branch_id", nullable = false)
    private UUID branchId;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "available_stock", nullable = false)
    private int availableStock;

    @Column(name = "reserved_stock", nullable = false)
    private int reservedStock;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected BranchInventoryEntity() {}

    public BranchInventoryEntity(
            UUID id,
            UUID branchId,
            UUID productId,
            int availableStock,
            int reservedStock,
            Instant now) {
        this.id = id;
        this.branchId = branchId;
        this.productId = productId;
        this.availableStock = availableStock;
        this.reservedStock = reservedStock;
        this.version = 0L;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void addAvailableStock(int positiveDelta, Instant now) {
        if (positiveDelta <= 0) {
            throw new IllegalArgumentException("Delta must be positive");
        }
        this.availableStock += positiveDelta;
        this.updatedAt = now;
    }

    public void adjustAvailableStock(int delta, Instant now) {
        int nextAvailable = this.availableStock + delta;
        if (nextAvailable < 0) {
            throw new IllegalStateException("Available stock cannot become negative");
        }
        this.availableStock = nextAvailable;
        this.updatedAt = now;
    }

    public void reserve(int quantity, Instant now) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }
        if (this.availableStock < quantity) {
            throw new IllegalStateException("Insufficient available stock to reserve");
        }
        this.availableStock -= quantity;
        this.reservedStock += quantity;
        this.updatedAt = now;
    }

    public void confirmReservation(int quantity, Instant now) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }
        if (this.reservedStock < quantity) {
            throw new IllegalStateException("Insufficient reserved stock to confirm");
        }
        this.reservedStock -= quantity;
        this.updatedAt = now;
    }

    public void releaseReservation(int quantity, Instant now) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }
        if (this.reservedStock < quantity) {
            throw new IllegalStateException("Insufficient reserved stock to release");
        }
        this.reservedStock -= quantity;
        this.availableStock += quantity;
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

    public int getAvailableStock() {
        return availableStock;
    }

    public int getReservedStock() {
        return reservedStock;
    }

    public long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
