package com.flowerchain.payment.internal;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentWebhookEventRepository extends JpaRepository<PaymentWebhookEventEntity, UUID> {

    boolean existsByProviderAndProviderEventId(String provider, String providerEventId);
}
