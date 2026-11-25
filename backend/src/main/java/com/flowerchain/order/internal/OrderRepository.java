package com.flowerchain.order.internal;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<OrderEntity, UUID> {

    List<OrderEntity> findByCustomerIdOrderByCreatedAtDesc(UUID customerId);

    List<OrderEntity> findAllByOrderByCreatedAtDesc();
}
