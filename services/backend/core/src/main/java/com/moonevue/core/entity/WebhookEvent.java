package com.moonevue.core.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;

@Getter
@Setter
@Entity
@Table(name = "webhook_events",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_webhook_provider_event_key", columnNames = {"provider", "event_key"})
        },
        indexes = {
                @Index(name = "idx_webhook_provider", columnList = "provider"),
                @Index(name = "idx_webhook_processed", columnList = "processed"),
                @Index(name = "idx_webhook_created_at", columnList = "created_at")
        }
)
public class WebhookEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Size(max = 40)
    @NotNull
    @Column(name = "provider", nullable = false, length = 40)
    private String provider;

    @Size(max = 200)
    @NotNull
    @Column(name = "event_key", nullable = false, length = 200)
    private String eventKey;

    @NotNull
    @Column(name = "payload", nullable = false, columnDefinition = "text")
    private String payload;

    @NotNull
    @Column(name = "processed", nullable = false)
    private Boolean processed = false;

    @Size(max = 40)
    @Column(name = "result", length = 40)
    private String result;

    @Size(max = 1000)
    @Column(name = "message", length = 1000)
    private String message;

    @Column(name = "processed_at")
    private OffsetDateTime processedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;
}
