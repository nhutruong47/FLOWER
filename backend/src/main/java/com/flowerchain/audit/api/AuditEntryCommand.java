package com.flowerchain.audit.api;

import java.util.UUID;

public record AuditEntryCommand(
        String category,
        String action,
        UUID branchId,
        String targetEntityType,
        UUID targetEntityId,
        UUID actorId,
        String actorType,
        String oldValuesJson,
        String newValuesJson) {}
