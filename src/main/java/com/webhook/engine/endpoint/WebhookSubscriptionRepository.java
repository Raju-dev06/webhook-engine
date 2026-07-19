package com.webhook.engine.endpoint;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WebhookSubscriptionRepository extends JpaRepository<WebhookSubscription, Long> {
    List<WebhookSubscription> findByEventType(String eventType);
    Optional<WebhookSubscription> findByEndpointIdAndEventType(Long endpointId, String eventType);
    List<WebhookSubscription> findByEndpointId(Long endpointId);
}
