package com.moonevue.core.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;

/**
 * Registra uma trilha de auditoria para todas as ações importantes no sistema
 * (criação, atualização, exclusão de entidades críticas).
 */
@Getter
@Setter
@Entity
@Table(name = "audit_logs", indexes = {
        @Index(name = "idx_audit_tenant_user", columnList = "tenant_id, user_id"),
        @Index(name = "idx_audit_entity", columnList = "entity_name, entity_id")
})
public class AuditLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id")
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user; // Usuário que realizou a ação

    @NotNull
    @Column(name = "action_type", nullable = false, length = 50)
    private String actionType; // ex: 'CLIENT_CREATED', 'BANK_ACCOUNT_UPDATED'

    @NotNull
    @Column(name = "entity_name", nullable = false, length = 50)
    private String entityName; // ex: 'Client', 'BankAccount'

    @NotNull
    @Column(name = "entity_id", nullable = false)
    private String entityId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "changes")
    private String changes; // JSON com o "antes" e "depois"

    @CreationTimestamp
    @Column(name = "action_timestamp", nullable = false)
    private OffsetDateTime actionTimestamp;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;
}
