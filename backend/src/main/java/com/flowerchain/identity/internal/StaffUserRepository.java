package com.flowerchain.identity.internal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StaffUserRepository extends JpaRepository<StaffUserEntity, UUID> {

    Optional<StaffUserEntity> findByEmailIgnoreCase(String email);
}
