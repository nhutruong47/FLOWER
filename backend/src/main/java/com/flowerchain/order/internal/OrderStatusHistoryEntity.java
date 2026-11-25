package com.flowerchain.order.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "order_status_history")
public class OrderStatusHistoryEntity {

    @Id
    private UUID id;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "from_status", length = 32)
    private String fromStatus;

    @Column(name = "to_status", nullable = false, length = 32)
    private String toStatus;

    @Column(name = "previous_branch_id")
    private UUID previousBranchId;

    @Column(name = "new_branch_id")
    private UUID newBranchId;

    @Column(name = "actor_id")
    private UUID actorId;

    @Column(name = "actor_type", nullable = false, length = 16)
    private String actorType;

    @Column(name = "reason_note", length = 500)
    private String reasonNote;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected OrderStatusHistoryEntity() {}

    public OrderStatusHistoryEntity(
            UUID id,
            UUID orderId,
            String fromStatus,
            String toStatus,
            UUID previousBranchId,
            UUID newBranchId,
            UUID actorId,
            String actorType,
            String reasonNote,
            Instant now) {
        this.id = id;
        this.orderId = orderId;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.previousBranchId = previousBranchId;
        this.newBranchId = newBranchId;
        this.actorId = actorId;
        this.actorType = actorType;
        this.reasonNote = reasonNote;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public String getFromStatus() {
        return fromStatus;
    }

    public String getToStatus() {
        return toStatus;
    }
}
