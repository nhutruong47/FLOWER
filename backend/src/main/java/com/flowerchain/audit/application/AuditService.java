package com.flowerchain.audit.application;

import com.flowerchain.audit.api.AuditApi;
import com.flowerchain.audit.api.AuditEntryCommand;
import com.flowerchain.audit.api.AuditLogDto;
import com.flowerchain.audit.internal.AuditLogEntity;
import com.flowerchain.audit.internal.AuditLogRepository;
import com.flowerchain.shared.api.PageResponse;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditService implements AuditApi {

    private final AuditLogRepository repository;
    private final Clock clock;

    public AuditService(AuditLogRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void record(AuditEntryCommand command) {
        Instant now = Instant.now(clock);
        repository.save(new AuditLogEntity(
                UUID.randomUUID(),
                command.category(),
                command.action(),
                command.branchId(),
                command.targetEntityType(),
                command.targetEntityId(),
                command.actorId(),
                command.actorType(),
                command.oldValuesJson(),
                command.newValuesJson(),
                now));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<AuditLogDto> query(
            Set<UUID> allowedBranchIds,
            boolean includeGlobal,
            Optional<UUID> filterBranchId,
            Optional<String> category,
            int page,
            int size) {
        List<AuditLogDto> filtered = repository.findAllByOrderByCreatedAtDesc().stream()
                .filter(e -> {
                    if (filterBranchId.isPresent()) {
                        return filterBranchId.get().equals(e.getBranchId());
                    }
                    if (includeGlobal) {
                        return true;
                    }
                    return e.getBranchId() != null && allowedBranchIds.contains(e.getBranchId());
                })
                .filter(e -> category.map(c -> c.equalsIgnoreCase(e.getCategory())).orElse(true))
                .map(e -> new AuditLogDto(
                        e.getId(),
                        e.getCategory(),
                        e.getAction(),
                        e.getBranchId(),
                        e.getTargetEntityType(),
                        e.getTargetEntityId(),
                        e.getActorId(),
                        e.getActorType(),
                        e.getOldValues(),
                        e.getNewValues(),
                        e.getCreatedAt()))
                .toList();
        return PageResponse.of(filtered, page, size);
    }
}
