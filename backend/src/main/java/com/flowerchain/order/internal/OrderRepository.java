package com.flowerchain.order.internal;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface OrderRepository extends JpaRepository<OrderEntity, UUID> {

    List<OrderEntity> findByCustomerIdOrderByCreatedAtDesc(UUID customerId);

    List<OrderEntity> findAllByOrderByCreatedAtDesc();

    /** Orders processed by one of the given branches and created in the half-open range {@code [from, to)}. */
    List<OrderEntity> findByProcessingBranchIdInAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtAsc(
            Collection<UUID> processingBranchIds, Instant from, Instant to);

    /**
     * Next value of the {@code order_number_seq} database sequence (TASK-018). The sequence survives application
     * restarts, so {@code ORD-} numbers never repeat. Read-write transaction because {@code nextval} is rejected
     * inside a read-only one.
     */
    @Transactional
    @Query(value = "SELECT nextval('order_number_seq')", nativeQuery = true)
    Long nextOrderNumber();

    /** Loads the order with {@code SELECT ... FOR UPDATE}, serialising verified payment events per order. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM OrderEntity o WHERE o.id = :id")
    Optional<OrderEntity> findByIdForUpdate(@Param("id") UUID id);
}
