package com.flowerchain.audit.api;

import com.flowerchain.shared.api.PageResponse;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface AuditApi {

    void record(AuditEntryCommand command);

    PageResponse<AuditLogDto> query(
            Set<UUID> allowedBranchIds,
            boolean includeGlobal,
            Optional<UUID> filterBranchId,
            Optional<String> category,
            int page,
            int size);
}
