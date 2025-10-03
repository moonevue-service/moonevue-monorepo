package com.moonevue.core.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.Map;

@Getter
@Setter
@Entity
@Table(name = "transaction_logs", schema = "public", indexes = {
        @Index(name = "idx_tx_log_transaction", columnList = "transaction_id"),
        @Index(name = "idx_tx_log_event_type", columnList = "event_type"),
        @Index(name = "idx_tx_log_severity", columnList = "severity"),
        @Index(name = "idx_tx_log_event_date", columnList = "event_date")
})
public class TransactionLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "log_id", nullable = false)
    private Long id;

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

    @Size(max = 20)
    @NotNull
    @ColumnDefault("'INFO'")
    @Column(name = "severity", nullable = false, length = 20)
    private String severity;

    @Column(name = "http_status_code")
    private Integer httpStatusCode;

    @Size(max = 50)
    @Column(name = "error_code", length = 50)
    private String errorCode;

    @NotNull
    @ColumnDefault("'{}'")
    @Column(name = "metadata", nullable = false)
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> metadata;

    @Size(max = 45)
    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Size(max = 500)
    @Column(name = "user_agent", length = 500)
    private String userAgent;

    @NotNull
    @ColumnDefault("now()")
    @Column(name = "event_date", nullable = false)
    private OffsetDateTime eventDate;

}