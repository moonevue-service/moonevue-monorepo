package com.moonevue.core.repository;

import com.moonevue.core.entity.WebhookEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface WebhookEventRepository extends JpaRepository<WebhookEvent, Long> {
    boolean existsByProviderAndEventKey(String provider, String eventKey);

    Optional<WebhookEvent> findByProviderAndEventKey(String provider, String eventKey);
}
