package com.flowerchain.shared.internal;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecordEntity, UUID> {

    Optional<IdempotencyRecordEntity> findByIdempotencyKeyAndActorScopeAndEndpointPattern(
            String idempotencyKey, String actorScope, String endpointPattern);
}
