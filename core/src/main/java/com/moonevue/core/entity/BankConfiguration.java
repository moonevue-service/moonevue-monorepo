package com.moonevue.core.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.Map;

@Getter
@Setter
@Entity
@Table(name = "bank_configurations", schema = "public", indexes = {
        @Index(name = "idx_bank_config_contractor", columnList = "contractor_id"),
        @Index(name = "idx_bank_config_environment", columnList = "environment"),
        @Index(name = "idx_bank_config_active", columnList = "is_active")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_contractor_bank_env", columnNames = {"contractor_id", "bank_account_id", "environment"})
})
public class BankConfiguration {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "config_id", nullable = false)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "contractor_id", nullable = false)
    private Contractor contractor;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bank_account_id", nullable = false)
    private BankAccount bankAccount;

    @Size(max = 500)
    @NotNull
    @Column(name = "api_key", nullable = false, length = 500)
    private String apiKey;

    @Size(max = 500)
    @Column(name = "api_secret", length = 500)
    private String apiSecret;

    @Size(max = 20)
    @NotNull
    @ColumnDefault("'SANDBOX'")
    @Column(name = "environment", nullable = false, length = 20)
    private String environment;

    @NotNull
    @ColumnDefault("'{}'")
    @Column(name = "extra_config", nullable = false)
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> extraConfig;

    @NotNull
    @ColumnDefault("true")
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = false;

    @Size(max = 500)
    @Column(name = "webhook_url", length = 500)
    private String webhookUrl;

    @NotNull
    @ColumnDefault("now()")
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @Column(name = "last_sync_at")
    private OffsetDateTime lastSyncAt;

}