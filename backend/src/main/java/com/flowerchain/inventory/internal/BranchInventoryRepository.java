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

    /**
     * Available stock as a plain value. Used by the unlocked availability checks so they never load a managed
     * {@link BranchInventoryEntity}: a later {@link #findByBranchIdAndProductIdForUpdate} in the same transaction then
     * reads the row fresh under its lock instead of failing a version check against a stale copy (TASK-018).
     */
    @Query("SELECT bi.availableStock FROM BranchInventoryEntity bi WHERE bi.branchId = :branchId AND bi.productId = :productId")
    Optional<Integer> findAvailableStock(@Param("branchId") UUID branchId, @Param("productId") UUID productId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT bi FROM BranchInventoryEntity bi WHERE bi.branchId = :branchId AND bi.productId = :productId")
    Optional<BranchInventoryEntity> findByBranchIdAndProductIdForUpdate(
            @Param("branchId") UUID branchId, @Param("productId") UUID productId);

    List<BranchInventoryEntity> findByBranchIdOrderByUpdatedAtDesc(UUID branchId);
}
