package com.moonevue.core.entity;

import com.moonevue.core.enums.ApiKeyEnvironment;
import com.moonevue.core.enums.ApiKeyStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;

/**
 * Credencial de integração programática (API Key) de um tenant.
 *
 * O segredo apresentado ao cliente nunca é persistido em claro: guardamos apenas
 * {@code secretHash}. A parte pública {@code keyId} é indexável e pode aparecer
 * em logs e na interface de gestão.
 */
@Getter
@Setter
@Entity
@Table(name = "api_keys",
        indexes = {
                @Index(name = "idx_api_keys_tenant", columnList = "tenant_id"),
                @Index(name = "idx_api_keys_status", columnList = "status"),
                @Index(name = "uk_api_keys_key_id", columnList = "key_id", unique = true)
        }
)
public class ApiKey {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "api_key_id", nullable = false)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @Size(max = 32)
    @NotNull
    @Column(name = "key_id", nullable = false, length = 32, unique = true)
    private String keyId;

    @Size(max = 255)
    @NotNull
    @Column(name = "secret_hash", nullable = false, length = 255)
    private String secretHash;

    @Size(max = 120)
    @NotNull
    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "environment", nullable = false, length = 10)
    private ApiKeyEnvironment environment;

    /** Escopos separados por vírgula, ex.: {@code charges:write,charges:read}. */
    @Column(name = "scopes", columnDefinition = "text")
    private String scopes;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ApiKeyStatus status = ApiKeyStatus.ACTIVE;

    @Column(name = "last_used_at")
    private OffsetDateTime lastUsedAt;

    @Column(name = "expires_at")
    private OffsetDateTime expiresAt;

    @Column(name = "created_by")
    private Long createdBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "revoked_at")
    private OffsetDateTime revokedAt;

    @Column(name = "revoked_by")
    private Long revokedBy;
}
