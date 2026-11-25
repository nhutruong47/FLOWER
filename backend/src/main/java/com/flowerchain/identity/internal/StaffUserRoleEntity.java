package com.flowerchain.identity.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "staff_user_roles")
public class StaffUserRoleEntity {

    @Id
    private UUID id;

    @Column(name = "staff_user_id", nullable = false)
    private UUID staffUserId;

    @Column(name = "role_code", nullable = false, length = 32)
    private String roleCode;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected StaffUserRoleEntity() {}

    public StaffUserRoleEntity(UUID id, UUID staffUserId, String roleCode, Instant now) {
        this.id = id;
        this.staffUserId = staffUserId;
        this.roleCode = roleCode;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public UUID getStaffUserId() { return staffUserId; }
    public String getRoleCode() { return roleCode; }
}
