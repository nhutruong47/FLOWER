package com.flowerchain.inventory.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "stock_transfers")
public class StockTransferEntity {

    @Id
    private UUID id;

    @Column(name = "transfer_number", nullable = false, unique = true, length = 64)
    private String transferNumber;

    @Column(name = "source_branch_id", nullable = false)
    private UUID sourceBranchId;

    @Column(name = "target_branch_id", nullable = false)
    private UUID targetBranchId;

    @Column(name = "status", nullable = false, length = 24)
    private String status;

    @Column(name = "requested_by_staff_id", nullable = false)
    private UUID requestedByStaffId;

    @Column(name = "approved_by_staff_id")
    private UUID approvedByStaffId;

    @Column(name = "dispatched_by_staff_id")
    private UUID dispatchedByStaffId;

    @Column(name = "received_by_staff_id")
    private UUID receivedByStaffId;

    @Column(name = "note", length = 500)
    private String note;

    @Column(name = "dispatched_at")
    private Instant dispatchedAt;

    @Column(name = "received_at")
    private Instant receivedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected StockTransferEntity() {}

    public StockTransferEntity(
            UUID id,
            String transferNumber,
            UUID sourceBranchId,
            UUID targetBranchId,
            String status,
            UUID requestedByStaffId,
            String note,
            Instant now) {
        this.id = id;
        this.transferNumber = transferNumber;
        this.sourceBranchId = sourceBranchId;
        this.targetBranchId = targetBranchId;
        this.status = status;
        this.requestedByStaffId = requestedByStaffId;
        this.note = note;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void markDispatched(UUID staffId, String dispatchNote, Instant now) {
        this.status = "IN_TRANSIT";
        this.dispatchedByStaffId = staffId;
        this.dispatchedAt = now;
        if (dispatchNote != null && !dispatchNote.isBlank()) {
            this.note = dispatchNote;
        }
        this.updatedAt = now;
    }

    public void markCompleted(UUID staffId, String receiveNote, Instant now) {
        this.status = "COMPLETED";
        this.receivedByStaffId = staffId;
        this.receivedAt = now;
        if (receiveNote != null && !receiveNote.isBlank()) {
            this.note = receiveNote;
        }
        this.updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public String getTransferNumber() {
        return transferNumber;
    }

    public UUID getSourceBranchId() {
        return sourceBranchId;
    }

    public UUID getTargetBranchId() {
        return targetBranchId;
    }

    public String getStatus() {
        return status;
    }

    public UUID getRequestedByStaffId() {
        return requestedByStaffId;
    }

    public UUID getApprovedByStaffId() {
        return approvedByStaffId;
    }

    public UUID getDispatchedByStaffId() {
        return dispatchedByStaffId;
    }

    public UUID getReceivedByStaffId() {
        return receivedByStaffId;
    }

    public String getNote() {
        return note;
    }

    public Instant getDispatchedAt() {
        return dispatchedAt;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
