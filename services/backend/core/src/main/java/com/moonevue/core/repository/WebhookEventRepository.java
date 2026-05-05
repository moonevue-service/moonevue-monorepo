package com.moonevue.core.repository;

import com.moonevue.core.entity.WebhookEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WebhookEventRepository extends JpaRepository<WebhookEvent, Long> {
    boolean existsByProviderAndEventKey(String provider, String eventKey);
}
