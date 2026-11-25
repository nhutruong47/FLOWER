package com.flowerchain.inventory.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "inventory_movements")
public class InventoryMovementEntity {

    @Id
    private UUID id;

    @Column(name = "branch_id", nullable = false)
    private UUID branchId;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "reason_code", nullable = false, length = 32)
    private String reasonCode;

    @Column(name = "quantity_delta", nullable = false)
    private int quantityDelta;

    @Column(name = "available_after", nullable = false)
    private int availableAfter;

    @Column(name = "reserved_after", nullable = false)
    private int reservedAfter;

    @Column(name = "source_document_type", nullable = false, length = 32)
    private String sourceDocumentType;

    @Column(name = "source_document_id", nullable = false)
    private UUID sourceDocumentId;

    @Column(name = "actor_id")
    private UUID actorId;

    @Column(name = "actor_type", nullable = false, length = 16)
    private String actorType;

    @Column(name = "note", length = 500)
    private String note;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected InventoryMovementEntity() {}

    public InventoryMovementEntity(
            UUID id,
            UUID branchId,
            UUID productId,
            String reasonCode,
            int quantityDelta,
            int availableAfter,
            int reservedAfter,
            String sourceDocumentType,
            UUID sourceDocumentId,
            UUID actorId,
            String actorType,
            String note,
            Instant now) {
        this.id = id;
        this.branchId = branchId;
        this.productId = productId;
        this.reasonCode = reasonCode;
        this.quantityDelta = quantityDelta;
        this.availableAfter = availableAfter;
        this.reservedAfter = reservedAfter;
        this.sourceDocumentType = sourceDocumentType;
        this.sourceDocumentId = sourceDocumentId;
        this.actorId = actorId;
        this.actorType = actorType;
        this.note = note;
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

    public String getReasonCode() {
        return reasonCode;
    }

    public int getQuantityDelta() {
        return quantityDelta;
    }

    public int getAvailableAfter() {
        return availableAfter;
    }

    public int getReservedAfter() {
        return reservedAfter;
    }

    public String getSourceDocumentType() {
        return sourceDocumentType;
    }

    public UUID getSourceDocumentId() {
        return sourceDocumentId;
    }

    public UUID getActorId() {
        return actorId;
    }

    public String getActorType() {
        return actorType;
    }

    public String getNote() {
        return note;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
