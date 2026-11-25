package com.flowerchain.identity.internal;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StaffUserRoleRepository extends JpaRepository<StaffUserRoleEntity, UUID> {

    List<StaffUserRoleEntity> findByStaffUserId(UUID staffUserId);

    void deleteByStaffUserId(UUID staffUserId);
}
