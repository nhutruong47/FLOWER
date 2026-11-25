package com.flowerchain.order.internal;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderLineRepository extends JpaRepository<OrderLineEntity, UUID> {

    List<OrderLineEntity> findByOrderId(UUID orderId);

    List<OrderLineEntity> findByOrderIdIn(Collection<UUID> orderIds);
}
