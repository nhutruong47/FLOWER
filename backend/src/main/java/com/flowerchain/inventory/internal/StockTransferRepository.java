package com.flowerchain.inventory.internal;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockTransferRepository extends JpaRepository<StockTransferEntity, UUID> {

    List<StockTransferEntity> findAllByOrderByCreatedAtDesc();
}
