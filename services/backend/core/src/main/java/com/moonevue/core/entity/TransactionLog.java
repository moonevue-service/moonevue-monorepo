package com.moonevue.core.entity;

import com.moonevue.core.enums.Severity;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.Map;

@Getter
@Setter
@Entity
@Table(name = "transaction_logs",
        indexes = {
                @Index(name = "idx_tx_log_tenant", columnList = "tenant_id"),
                @Index(name = "idx_tx_log_transaction", columnList = "transaction_id"),
                @Index(name = "idx_tx_log_event_type", columnList = "event_type"),
                @Index(name = "idx_tx_log_severity", columnList = "severity"),
                @Index(name = "idx_tx_log_event_date", columnList = "event_date")
        }
)
public class TransactionLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "log_id", nullable = false)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "transaction_id", nullable = false)
    private Transaction transaction;

    @Size(max = 50)
    @NotNull
    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType;

    @Size(max = 1000)
    @NotNull
    @Column(name = "message", nullable = false, length = 1000)
    private String message;

    @Enumerated(EnumType.STRING)
    @NotNull
    @Column(name = "severity", nullable = false, length = 20)
    private Severity severity = Severity.INFO;

    @Column(name = "http_status_code")
    private Integer httpStatusCode;

    @Size(max = 50)
    @Column(name = "error_code", length = 50)
    private String errorCode;

    @NotNull
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", nullable = false)
    private Map<String, Object> metadata = Map.of();

    @Size(max = 45)
    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Size(max = 500)
    @Column(name = "user_agent", length = 500)
    private String userAgent;

    @CreationTimestamp
    @Column(name = "event_date", nullable = false)
    private OffsetDateTime eventDate;
}