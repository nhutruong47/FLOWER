package com.flowerchain.identity.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "staff_branch_assignments")
public class StaffBranchAssignmentEntity {

    @Id
    private UUID id;

    @Column(name = "staff_user_id", nullable = false)
    private UUID staffUserId;

    @Column(name = "branch_id", nullable = false)
    private UUID branchId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected StaffBranchAssignmentEntity() {}

    public StaffBranchAssignmentEntity(UUID id, UUID staffUserId, UUID branchId, Instant now) {
        this.id = id;
        this.staffUserId = staffUserId;
        this.branchId = branchId;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public UUID getStaffUserId() { return staffUserId; }
    public UUID getBranchId() { return branchId; }
}
