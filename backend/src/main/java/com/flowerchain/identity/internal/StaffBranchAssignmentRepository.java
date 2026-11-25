package com.flowerchain.identity.internal;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StaffBranchAssignmentRepository extends JpaRepository<StaffBranchAssignmentEntity, UUID> {

    List<StaffBranchAssignmentEntity> findByStaffUserId(UUID staffUserId);

    void deleteByStaffUserId(UUID staffUserId);
}
