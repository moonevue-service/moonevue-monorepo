package com.moonevue.gateway.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Métricas de utilização das integrações (API Keys) de um tenant.
 *
 * <p>Agrega o inventário de chaves (api_keys) com o volume de cobranças
 * originadas pela API pública (transactions.source_channel = 'PUBLIC_API'),
 * atribuídas à chave de origem (transactions.api_key_id).
 */
public record IntegrationAnalyticsDTO(
        int rangeDays,
        KeysSummary keys,
        UsageSummary usage,
        List<DailyPoint> timeseries,
        List<StatusSlice> byStatus,
        List<EnvironmentSlice> byEnvironment,
        List<KeyUsage> perKey
) {
    public record KeysSummary(
            long total,
            long active,
            long revoked,
            long live,
            long test,
            long usedLast7d,
            long neverUsed
    ) {}

    public record UsageSummary(
            long totalCharges,
            BigDecimal totalAmount,
            long paidCharges,
            BigDecimal paidAmount,
            long chargesLast7d,
            double successRate
    ) {}

    public record DailyPoint(String date, long count, BigDecimal amount) {}

    public record StatusSlice(String status, long count) {}

    public record EnvironmentSlice(String environment, long count, BigDecimal amount) {}

    public record KeyUsage(
            long apiKeyId,
            String name,
            String environment,
            String status,
            OffsetDateTime lastUsedAt,
            long charges,
            BigDecimal amount,
            long paidCharges
    ) {}
}
