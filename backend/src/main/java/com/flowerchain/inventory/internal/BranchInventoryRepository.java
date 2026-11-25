package com.flowerchain.inventory.internal;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BranchInventoryRepository extends JpaRepository<BranchInventoryEntity, UUID> {

    Optional<BranchInventoryEntity> findByBranchIdAndProductId(UUID branchId, UUID productId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT bi FROM BranchInventoryEntity bi WHERE bi.branchId = :branchId AND bi.productId = :productId")
    Optional<BranchInventoryEntity> findByBranchIdAndProductIdForUpdate(
            @Param("branchId") UUID branchId, @Param("productId") UUID productId);

    List<BranchInventoryEntity> findByBranchIdOrderByUpdatedAtDesc(UUID branchId);
}
