package com.flowerchain.audit.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "audit_logs")
public class AuditLogEntity {

    @Id
    private UUID id;

    @Column(name = "category", nullable = false, length = 32)
    private String category;

    @Column(name = "action", nullable = false, length = 64)
    private String action;

    @Column(name = "branch_id")
    private UUID branchId;

    @Column(name = "target_entity_type", nullable = false, length = 64)
    private String targetEntityType;

    @Column(name = "target_entity_id")
    private UUID targetEntityId;

    @Column(name = "actor_id")
    private UUID actorId;

    @Column(name = "actor_type", nullable = false, length = 32)
    private String actorType;

    @Column(name = "old_values", columnDefinition = "text")
    private String oldValues;

    @Column(name = "new_values", columnDefinition = "text")
    private String newValues;

    @Column(name = "ip_address", length = 64)
    private String ipAddress;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AuditLogEntity() {}

    public AuditLogEntity(
            UUID id,
            String category,
            String action,
            UUID branchId,
            String targetEntityType,
            UUID targetEntityId,
            UUID actorId,
            String actorType,
            String oldValues,
            String newValues,
            Instant now) {
        this.id = id;
        this.category = category;
        this.action = action;
        this.branchId = branchId;
        this.targetEntityType = targetEntityType;
        this.targetEntityId = targetEntityId;
        this.actorId = actorId;
        this.actorType = actorType;
        this.oldValues = oldValues;
        this.newValues = newValues;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public UUID getId() { return id; }
    public String getCategory() { return category; }
    public String getAction() { return action; }
    public UUID getBranchId() { return branchId; }
    public String getTargetEntityType() { return targetEntityType; }
    public UUID getTargetEntityId() { return targetEntityId; }
    public UUID getActorId() { return actorId; }
    public String getActorType() { return actorType; }
    public String getOldValues() { return oldValues; }
    public String getNewValues() { return newValues; }
    public Instant getCreatedAt() { return createdAt; }
}
