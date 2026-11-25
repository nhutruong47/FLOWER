package com.flowerchain.inventory.internal;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

public interface StockTransferRepository extends JpaRepository<StockTransferEntity, UUID> {

    List<StockTransferEntity> findAllByOrderByCreatedAtDesc();

    /**
     * Next value of the {@code stock_transfer_number_seq} database sequence (TASK-018). The sequence survives
     * application restarts, so {@code TRF-} numbers never repeat. Read-write transaction because {@code nextval}
     * is rejected inside a read-only one.
     */
    @Transactional
    @Query(value = "SELECT nextval('stock_transfer_number_seq')", nativeQuery = true)
    Long nextTransferNumber();
}
