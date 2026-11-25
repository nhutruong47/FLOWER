package com.flowerchain.branch.internal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BranchRepository extends JpaRepository<BranchEntity, UUID> {

    Optional<BranchEntity> findByCodeIgnoreCase(String code);

    List<BranchEntity> findByStatusOrderByCodeAsc(String status);
}
