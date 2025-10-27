package com.moonevue.core.entity;

import com.moonevue.core.enums.Environment;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
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

    @Enumerated(EnumType.STRING)
    @NotNull
    @Column(name = "environment", nullable = false, length = 20)
    private Environment environment = Environment.SANDBOX;

    @NotNull
    @Column(name = "extra_config", nullable = false)
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> extraConfig = Map.of();

    @NotNull
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Size(max = 500)
    @Column(name = "webhook_url", length = 500)
    private String webhookUrl;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @Column(name = "last_sync_at")
    private OffsetDateTime lastSyncAt;

    // Recomenda-se armazenar certificados/senhas em cofre (ex.: HashiCorp Vault, AWS Secrets Manager)
    @Column(name = "certificate_path")
    private String certificatePath;

    @Column(name = "certificate_password")
    private String certificatePassword;

    public BankConfiguration() {
    }

    public BankConfiguration(String certPath, String certPassword) {
    }
}
