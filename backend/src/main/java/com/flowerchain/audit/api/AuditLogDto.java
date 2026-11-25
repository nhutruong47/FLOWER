package com.flowerchain.audit.api;

import java.time.Instant;
import java.util.UUID;

public record AuditLogDto(
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
        Instant createdAt) {}
