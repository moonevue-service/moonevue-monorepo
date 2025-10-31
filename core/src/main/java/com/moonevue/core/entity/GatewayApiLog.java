package com.moonevue.core.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "gateway_api_logs",
        indexes = {
                @Index(name = "idx_gateway_log_tenant", columnList = "tenant_id"),
                @Index(name = "idx_gateway_log_correlation", columnList = "correlation_id")
        }
)
public class GatewayApiLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @Column(name = "correlation_id", nullable = false)
    private UUID correlationId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id")
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bank_configuration_id")
    private BankConfiguration bankConfiguration;

    @NotNull
    @Column(name = "request_url", nullable = false, columnDefinition = "TEXT")
    private String requestUrl;

    @NotNull
    @Column(name = "request_method", nullable = false, length = 10)
    private String requestMethod;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "request_headers")
    private Map<String, Object> requestHeaders;

    @Column(name = "request_body", columnDefinition = "TEXT")
    private String requestBody;

    @Column(name = "response_status_code")
    private Integer responseStatusCode;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "response_headers")
    private Map<String, Object> responseHeaders;

    @Column(name = "response_body", columnDefinition = "TEXT")
    private String responseBody;

    @Column(name = "duration_ms")
    private Long durationMs;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;
}