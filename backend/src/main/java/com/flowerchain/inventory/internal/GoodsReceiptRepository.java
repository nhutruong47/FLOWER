package com.flowerchain.inventory.internal;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

public interface GoodsReceiptRepository extends JpaRepository<GoodsReceiptEntity, UUID> {

    /**
     * Next value of the {@code goods_receipt_number_seq} database sequence (TASK-018). The sequence survives
     * application restarts, so {@code GR-} numbers never repeat. Read-write transaction because {@code nextval}
     * is rejected inside a read-only one.
     */
    @Transactional
    @Query(value = "SELECT nextval('goods_receipt_number_seq')", nativeQuery = true)
    Long nextReceiptNumber();
}
