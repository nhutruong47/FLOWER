package com.flowerchain.shared.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "idempotency_records")
public class IdempotencyRecordEntity {

    @Id
    private UUID id;

    @Column(name = "idempotency_key", nullable = false, length = 128)
    private String idempotencyKey;

    @Column(name = "actor_scope", nullable = false, length = 128)
    private String actorScope;

    @Column(name = "endpoint_pattern", nullable = false, length = 128)
    private String endpointPattern;

    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    @Column(name = "response_status", nullable = false)
    private int responseStatus;

    @Column(name = "response_body", nullable = false, columnDefinition = "text")
    private String responseBody;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected IdempotencyRecordEntity() {}

    public IdempotencyRecordEntity(
            UUID id,
            String idempotencyKey,
            String actorScope,
            String endpointPattern,
            String requestHash,
            int responseStatus,
            String responseBody,
            Instant expiresAt,
            Instant now) {
        this.id = id;
        this.idempotencyKey = idempotencyKey;
        this.actorScope = actorScope;
        this.endpointPattern = endpointPattern;
        this.requestHash = requestHash;
        this.responseStatus = responseStatus;
        this.responseBody = responseBody;
        this.expiresAt = expiresAt;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public String getRequestHash() {
        return requestHash;
    }

    public String getResponseBody() {
        return responseBody;
    }
}
