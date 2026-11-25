package com.flowerchain.inventory.internal;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockAdjustmentRepository extends JpaRepository<StockAdjustmentEntity, UUID> {}
