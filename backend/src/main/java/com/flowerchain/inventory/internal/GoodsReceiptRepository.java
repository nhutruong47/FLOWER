package com.flowerchain.inventory.internal;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GoodsReceiptRepository extends JpaRepository<GoodsReceiptEntity, UUID> {}
