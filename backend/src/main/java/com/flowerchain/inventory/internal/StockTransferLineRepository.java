package com.flowerchain.inventory.internal;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockTransferLineRepository extends JpaRepository<StockTransferLineEntity, UUID> {

    List<StockTransferLineEntity> findByStockTransferId(UUID stockTransferId);
}
