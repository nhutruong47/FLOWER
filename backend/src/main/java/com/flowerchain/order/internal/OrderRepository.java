package com.flowerchain.order.internal;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

public interface OrderRepository extends JpaRepository<OrderEntity, UUID> {

    List<OrderEntity> findByCustomerIdOrderByCreatedAtDesc(UUID customerId);

    List<OrderEntity> findAllByOrderByCreatedAtDesc();

    /**
     * Next value of the {@code order_number_seq} database sequence (TASK-018). The sequence survives application
     * restarts, so {@code ORD-} numbers never repeat. Read-write transaction because {@code nextval} is rejected
     * inside a read-only one.
     */
    @Transactional
    @Query(value = "SELECT nextval('order_number_seq')", nativeQuery = true)
    Long nextOrderNumber();
}
