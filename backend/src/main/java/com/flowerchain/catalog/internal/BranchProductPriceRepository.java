package com.flowerchain.catalog.internal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BranchProductPriceRepository extends JpaRepository<BranchProductPriceEntity, UUID> {

    Optional<BranchProductPriceEntity> findByBranchIdAndProductId(UUID branchId, UUID productId);

    List<BranchProductPriceEntity> findByBranchIdAndSellableTrue(UUID branchId);
}
