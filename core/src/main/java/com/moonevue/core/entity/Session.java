package com.moonevue.core.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity @Table(name = "auth_session")
@Getter @Setter @NoArgsConstructor
public class Session {
    @Id @UuidGenerator
    private UUID id;
    @ManyToOne(optional = false) private User user;
    @Column(nullable = false) private OffsetDateTime createdAt = OffsetDateTime.now();
    @Column(nullable = false) private OffsetDateTime lastSeenAt = OffsetDateTime.now();
    @Column(nullable = false) private OffsetDateTime expiresAt;
    private String ipAddress;
    private String userAgent;
    @Column(nullable = false) private boolean revoked = false;
}
