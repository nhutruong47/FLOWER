package com.flowerchain.identity.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "staff_refresh_sessions")
public class StaffRefreshSessionEntity {

    @Id
    private UUID id;

    @Column(name = "staff_user_id", nullable = false)
    private UUID staffUserId;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected StaffRefreshSessionEntity() {}

    public StaffRefreshSessionEntity(
            UUID id, UUID staffUserId, String tokenHash, Instant expiresAt, Instant now) {
        this.id = id;
        this.staffUserId = staffUserId;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void revoke(Instant now) {
        this.revokedAt = now;
        this.updatedAt = now;
    }

    public UUID getStaffUserId() { return staffUserId; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getRevokedAt() { return revokedAt; }
}
