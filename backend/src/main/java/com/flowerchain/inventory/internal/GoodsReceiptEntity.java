package com.flowerchain.inventory.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "goods_receipts")
public class GoodsReceiptEntity {

    @Id
    private UUID id;

    @Column(name = "branch_id", nullable = false)
    private UUID branchId;

    @Column(name = "receipt_number", nullable = false, unique = true, length = 64)
    private String receiptNumber;

    @Column(name = "supplier_name", nullable = false, length = 200)
    private String supplierName;

    @Column(name = "supplier_reference", length = 100)
    private String supplierReference;

    @Column(name = "received_by_staff_id", nullable = false)
    private UUID receivedByStaffId;

    @Column(name = "note", length = 500)
    private String note;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected GoodsReceiptEntity() {}

    public GoodsReceiptEntity(
            UUID id,
            UUID branchId,
            String receiptNumber,
            String supplierName,
            String supplierReference,
            UUID receivedByStaffId,
            String note,
            Instant now) {
        this.id = id;
        this.branchId = branchId;
        this.receiptNumber = receiptNumber;
        this.supplierName = supplierName;
        this.supplierReference = supplierReference;
        this.receivedByStaffId = receivedByStaffId;
        this.note = note;
        this.receivedAt = now;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public UUID getBranchId() {
        return branchId;
    }

    public String getReceiptNumber() {
        return receiptNumber;
    }

    public String getSupplierName() {
        return supplierName;
    }

    public String getSupplierReference() {
        return supplierReference;
    }

    public UUID getReceivedByStaffId() {
        return receivedByStaffId;
    }

    public String getNote() {
        return note;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }
}
