package com.flowerchain.inventory.internal;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GoodsReceiptLineRepository extends JpaRepository<GoodsReceiptLineEntity, UUID> {

    List<GoodsReceiptLineEntity> findByGoodsReceiptId(UUID goodsReceiptId);
}
