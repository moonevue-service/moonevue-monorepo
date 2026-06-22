package com.moonevue.gateway.service;

import com.moonevue.gateway.dto.IntegrationAnalyticsDTO;
import com.moonevue.gateway.dto.IntegrationAnalyticsDTO.DailyPoint;
import com.moonevue.gateway.dto.IntegrationAnalyticsDTO.EnvironmentSlice;
import com.moonevue.gateway.dto.IntegrationAnalyticsDTO.KeyUsage;
import com.moonevue.gateway.dto.IntegrationAnalyticsDTO.KeysSummary;
import com.moonevue.gateway.dto.IntegrationAnalyticsDTO.StatusSlice;
import com.moonevue.gateway.dto.IntegrationAnalyticsDTO.UsageSummary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * Agrega métricas de utilização das API Keys de um tenant para a aba de
 * Analytics em Integrações. Consultas read-only via {@link JdbcTemplate}.
 *
 * <p>Cobranças consideradas "via API" são aquelas com
 * {@code source_channel = 'PUBLIC_API'}, atribuídas à chave por
 * {@code api_key_id} (preenchido em {@code PaymentService.createCharge}).
 */
@Service
public class IntegrationAnalyticsService {

    /** Status que representam uma cobrança efetivamente paga/liquidada. */
    private static final String PAID_STATUSES = "'PAID','SETTLED','CAPTURED','CONFIRMED'";

    private final JdbcTemplate jdbc;

    public IntegrationAnalyticsService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public IntegrationAnalyticsDTO getAnalytics(Long tenantId, int rangeDays) {
        int days = rangeDays <= 0 ? 30 : Math.min(rangeDays, 365);

        KeysSummary keys = loadKeysSummary(tenantId);
        UsageSummary usage = loadUsageSummary(tenantId);
        List<DailyPoint> timeseries = loadTimeseries(tenantId, days);
        List<StatusSlice> byStatus = loadByStatus(tenantId);
        List<EnvironmentSlice> byEnvironment = loadByEnvironment(tenantId);
        List<KeyUsage> perKey = loadPerKey(tenantId);

        return new IntegrationAnalyticsDTO(days, keys, usage, timeseries, byStatus, byEnvironment, perKey);
    }

    private KeysSummary loadKeysSummary(Long tenantId) {
        return jdbc.queryForObject(
                """
                SELECT
                    COUNT(*)                                                              AS total,
                    COUNT(*) FILTER (WHERE status = 'ACTIVE')                             AS active,
                    COUNT(*) FILTER (WHERE status = 'REVOKED')                            AS revoked,
                    COUNT(*) FILTER (WHERE environment = 'LIVE')                          AS live,
                    COUNT(*) FILTER (WHERE environment = 'TEST')                          AS test,
                    COUNT(*) FILTER (WHERE last_used_at >= NOW() - INTERVAL '7 days')     AS used_last_7d,
                    COUNT(*) FILTER (WHERE last_used_at IS NULL)                          AS never_used
                FROM api_keys
                WHERE tenant_id = ?
                """,
                (rs, n) -> new KeysSummary(
                        rs.getLong("total"),
                        rs.getLong("active"),
                        rs.getLong("revoked"),
                        rs.getLong("live"),
                        rs.getLong("test"),
                        rs.getLong("used_last_7d"),
                        rs.getLong("never_used")
                ),
                tenantId
        );
    }

    private UsageSummary loadUsageSummary(Long tenantId) {
        return jdbc.queryForObject(
                """
                SELECT
                    COUNT(*)                                                                       AS total,
                    COALESCE(SUM(amount), 0)                                                        AS total_amount,
                    COUNT(*) FILTER (WHERE status IN (%s))                                          AS paid,
                    COALESCE(SUM(amount) FILTER (WHERE status IN (%s)), 0)                          AS paid_amount,
                    COUNT(*) FILTER (WHERE created_at >= NOW() - INTERVAL '7 days')                 AS last_7d
                FROM transactions
                WHERE tenant_id = ? AND source_channel = 'PUBLIC_API'
                """.formatted(PAID_STATUSES, PAID_STATUSES),
                (rs, n) -> {
                    long total = rs.getLong("total");
                    long paid = rs.getLong("paid");
                    double rate = total > 0 ? (double) paid / total : 0.0;
                    return new UsageSummary(
                            total,
                            rs.getBigDecimal("total_amount"),
                            paid,
                            rs.getBigDecimal("paid_amount"),
                            rs.getLong("last_7d"),
                            Math.round(rate * 1000.0) / 1000.0
                    );
                },
                tenantId
        );
    }

    private List<DailyPoint> loadTimeseries(Long tenantId, int days) {
        return jdbc.query(
                """
                SELECT
                    to_char(d.day, 'YYYY-MM-DD')      AS day,
                    COALESCE(t.cnt, 0)                AS cnt,
                    COALESCE(t.amount, 0)             AS amount
                FROM generate_series(
                        (CURRENT_DATE - make_interval(days => ? - 1)),
                        CURRENT_DATE,
                        INTERVAL '1 day'
                     ) AS d(day)
                LEFT JOIN (
                    SELECT date_trunc('day', created_at)::date AS day,
                           COUNT(*) AS cnt,
                           SUM(amount) AS amount
                    FROM transactions
                    WHERE tenant_id = ? AND source_channel = 'PUBLIC_API'
                      AND created_at >= CURRENT_DATE - make_interval(days => ? - 1)
                    GROUP BY 1
                ) t ON t.day = d.day
                ORDER BY d.day
                """,
                (rs, n) -> new DailyPoint(rs.getString("day"), rs.getLong("cnt"), rs.getBigDecimal("amount")),
                days, tenantId, days
        );
    }

    private List<StatusSlice> loadByStatus(Long tenantId) {
        return jdbc.query(
                """
                SELECT status, COUNT(*) AS cnt
                FROM transactions
                WHERE tenant_id = ? AND source_channel = 'PUBLIC_API'
                GROUP BY status
                ORDER BY cnt DESC
                """,
                (rs, n) -> new StatusSlice(rs.getString("status"), rs.getLong("cnt")),
                tenantId
        );
    }

    private List<EnvironmentSlice> loadByEnvironment(Long tenantId) {
        return jdbc.query(
                """
                SELECT COALESCE(k.environment, 'DESCONHECIDO') AS environment,
                       COUNT(t.id)                             AS cnt,
                       COALESCE(SUM(t.amount), 0)              AS amount
                FROM transactions t
                LEFT JOIN api_keys k ON k.api_key_id = t.api_key_id
                WHERE t.tenant_id = ? AND t.source_channel = 'PUBLIC_API'
                GROUP BY COALESCE(k.environment, 'DESCONHECIDO')
                ORDER BY cnt DESC
                """,
                (rs, n) -> new EnvironmentSlice(rs.getString("environment"), rs.getLong("cnt"), rs.getBigDecimal("amount")),
                tenantId
        );
    }

    private List<KeyUsage> loadPerKey(Long tenantId) {
        return jdbc.query(
                """
                SELECT
                    k.api_key_id,
                    k.name,
                    k.environment,
                    k.status,
                    k.last_used_at,
                    COUNT(t.id)                                                AS charges,
                    COALESCE(SUM(t.amount), 0)                                 AS amount,
                    COUNT(t.id) FILTER (WHERE t.status IN (%s))                AS paid
                FROM api_keys k
                LEFT JOIN transactions t
                       ON t.api_key_id = k.api_key_id
                      AND t.source_channel = 'PUBLIC_API'
                WHERE k.tenant_id = ?
                GROUP BY k.api_key_id, k.name, k.environment, k.status, k.last_used_at, k.created_at
                ORDER BY charges DESC, k.created_at DESC
                """.formatted(PAID_STATUSES),
                (rs, n) -> {
                    var ts = rs.getObject("last_used_at", OffsetDateTime.class);
                    return new KeyUsage(
                            rs.getLong("api_key_id"),
                            rs.getString("name"),
                            rs.getString("environment"),
                            rs.getString("status"),
                            ts != null ? ts.withOffsetSameInstant(ZoneOffset.UTC) : null,
                            rs.getLong("charges"),
                            rs.getBigDecimal("amount"),
                            rs.getLong("paid")
                    );
                },
                tenantId
        );
    }
}
