package com.flowerchain.identity.internal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StaffRefreshSessionRepository extends JpaRepository<StaffRefreshSessionEntity, UUID> {

    Optional<StaffRefreshSessionEntity> findByTokenHash(String tokenHash);

    List<StaffRefreshSessionEntity> findByStaffUserIdAndRevokedAtIsNull(UUID staffUserId);
}
