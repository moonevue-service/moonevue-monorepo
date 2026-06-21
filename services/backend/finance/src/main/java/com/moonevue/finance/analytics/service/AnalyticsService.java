package com.moonevue.finance.analytics.service;

import com.moonevue.core.enums.TransactionStatus;
import com.moonevue.core.enums.TransactionType;
import com.moonevue.finance.analytics.domain.Granularity;
import com.moonevue.finance.analytics.dto.AnalyticsDashboardResponse;
import com.moonevue.finance.analytics.dto.ClientRevenueResponse;
import com.moonevue.finance.analytics.dto.ExecutiveSummaryResponse;
import com.moonevue.finance.analytics.dto.InsightResponse;
import com.moonevue.finance.analytics.dto.ReceivablesResponse;
import com.moonevue.finance.analytics.dto.RevenueTimeSeriesResponse;
import com.moonevue.finance.analytics.dto.StatusBreakdownResponse;
import com.moonevue.finance.analytics.repository.AnalyticsRepository;
import com.moonevue.finance.analytics.repository.projection.ClientRevenueProjection;
import com.moonevue.finance.analytics.repository.projection.ReceivablesProjection;
import com.moonevue.finance.analytics.repository.projection.RevenueSummaryProjection;
import com.moonevue.finance.analytics.repository.projection.StatusBreakdownProjection;
import com.moonevue.finance.analytics.repository.projection.TimeSeriesProjection;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * Camada de regras de negócio analíticas sobre os dados do Finance.
 * Read-only e cacheada (TTL curto) para dashboards em tempo quase real.
 */
@Service
@RequiredArgsConstructor
public class AnalyticsService {

    /** Status que representam dinheiro efetivamente recebido. */
    static final List<TransactionStatus> PAID_STATUSES = List.of(
            TransactionStatus.PAID, TransactionStatus.SETTLED, TransactionStatus.CAPTURED);

    /** Status que representam valores a receber (em aberto). */
    static final List<TransactionStatus> RECEIVABLE_STATUSES = List.of(
            TransactionStatus.PENDING, TransactionStatus.AUTHORIZED, TransactionStatus.PROCESSING);

    private static final List<String> PAID_STATUS_NAMES =
            PAID_STATUSES.stream().map(Enum::name).toList();

    private final AnalyticsRepository repository;
    private final InsightEngine insightEngine;

    @Cacheable(cacheNames = "analytics-dashboard",
            key = "#tenantId + ':' + #from + ':' + #to + ':' + #granularity + ':' + #topClients")
    @Transactional(readOnly = true)
    public AnalyticsDashboardResponse getDashboard(Long tenantId, LocalDate from, LocalDate to,
                                                   Granularity granularity, int topClients) {
        ExecutiveSummaryResponse summary = getSummary(tenantId, from, to, granularity);
        RevenueTimeSeriesResponse series = getRevenueTimeSeries(tenantId, from, to, granularity);
        List<ClientRevenueResponse> clients = getClientRanking(tenantId, from, to, topClients);
        List<StatusBreakdownResponse> status = getStatusBreakdown(tenantId, from, to);
        ReceivablesResponse receivables = getReceivables(tenantId);
        List<InsightResponse> insights =
                insightEngine.generate(summary, clients, receivables, status);
        return new AnalyticsDashboardResponse(summary, series, clients, status, receivables, insights);
    }

    @Transactional(readOnly = true)
    public ExecutiveSummaryResponse getSummary(Long tenantId, LocalDate from, LocalDate to,
                                               Granularity granularity) {
        PeriodAgg current = aggregate(tenantId, from, to);

        // Período anterior de mesma duração, imediatamente antes do atual.
        long days = java.time.temporal.ChronoUnit.DAYS.between(from, to);
        LocalDate prevTo = from.minusDays(1);
        LocalDate prevFrom = prevTo.minusDays(days);
        PeriodAgg previous = aggregate(tenantId, prevFrom, prevTo);

        BigDecimal averageTicket = current.paidCount > 0
                ? current.grossRevenue.divide(BigDecimal.valueOf(current.paidCount), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        BigDecimal netProfit = current.netRevenue.subtract(current.totalRefunds);

        var growth = new ExecutiveSummaryResponse.Growth(
                growthPct(current.grossRevenue, previous.grossRevenue),
                growthPct(current.netRevenue, previous.netRevenue),
                growthPct(BigDecimal.valueOf(current.paidCount), BigDecimal.valueOf(previous.paidCount)));

        return new ExecutiveSummaryResponse(
                new ExecutiveSummaryResponse.Period(from, to, granularity),
                scale(current.grossRevenue),
                scale(current.netRevenue),
                scale(current.totalFees),
                scale(current.totalRefunds),
                scale(netProfit),
                averageTicket,
                ratioPct(current.netRevenue, current.grossRevenue),
                ratioPct(BigDecimal.valueOf(current.paidCount), BigDecimal.valueOf(current.totalCount)),
                current.paidCount,
                current.totalCount,
                current.payingClients,
                growth);
    }

    @Transactional(readOnly = true)
    public RevenueTimeSeriesResponse getRevenueTimeSeries(Long tenantId, LocalDate from, LocalDate to,
                                                          Granularity granularity) {
        List<TimeSeriesProjection> rows = repository.revenueTimeSeries(
                tenantId, startOf(from), endOf(to), granularity.sql(), PAID_STATUS_NAMES);
        List<RevenueTimeSeriesResponse.Point> points = rows.stream()
                .map(r -> new RevenueTimeSeriesResponse.Point(
                        r.getBucket().atOffset(ZoneOffset.UTC).toLocalDate(),
                        scale(nz(r.getGrossRevenue())),
                        scale(nz(r.getNetRevenue())),
                        r.getPaidCount() == null ? 0L : r.getPaidCount()))
                .toList();
        return new RevenueTimeSeriesResponse(granularity, points);
    }

    @Transactional(readOnly = true)
    public List<ClientRevenueResponse> getClientRanking(Long tenantId, LocalDate from, LocalDate to,
                                                        int topClients) {
        List<ClientRevenueProjection> rows = repository.rankClientsByRevenue(
                tenantId, startOf(from), endOf(to), TransactionType.CHARGE, PAID_STATUSES,
                PageRequest.of(0, Math.max(1, topClients)));
        BigDecimal total = rows.stream()
                .map(r -> nz(r.getRevenue()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<ClientRevenueResponse> result = new java.util.ArrayList<>(rows.size());
        int rank = 1;
        for (ClientRevenueProjection r : rows) {
            BigDecimal revenue = nz(r.getRevenue());
            result.add(new ClientRevenueResponse(
                    rank++,
                    r.getClientId(),
                    r.getClientName(),
                    scale(revenue),
                    r.getTxCount() == null ? 0L : r.getTxCount(),
                    ratioPct(revenue, total)));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public List<StatusBreakdownResponse> getStatusBreakdown(Long tenantId, LocalDate from, LocalDate to) {
        List<StatusBreakdownProjection> rows =
                repository.breakdownByStatus(tenantId, startOf(from), endOf(to));
        BigDecimal total = rows.stream()
                .map(r -> nz(r.getTotalAmount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return rows.stream()
                .map(r -> new StatusBreakdownResponse(
                        r.getStatus(),
                        r.getTxCount() == null ? 0L : r.getTxCount(),
                        scale(nz(r.getTotalAmount())),
                        ratioPct(nz(r.getTotalAmount()), total)))
                .toList();
    }

    @Transactional(readOnly = true)
    public ReceivablesResponse getReceivables(Long tenantId) {
        ReceivablesProjection r = repository.aggregateReceivables(
                tenantId, LocalDate.now(ZoneOffset.UTC), RECEIVABLE_STATUSES);
        BigDecimal totalReceivable = scale(nz(r.getTotalReceivable()));
        BigDecimal totalOverdue = scale(nz(r.getTotalOverdue()));
        BigDecimal totalToDue = scale(nz(r.getTotalToDue()));
        return new ReceivablesResponse(
                totalReceivable, totalOverdue, totalToDue,
                ratioPct(totalOverdue, totalReceivable));
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private PeriodAgg aggregate(Long tenantId, LocalDate from, LocalDate to) {
        OffsetDateTime fromTs = startOf(from);
        OffsetDateTime toTs = endOf(to);
        RevenueSummaryProjection rev = repository.aggregateRevenue(
                tenantId, fromTs, toTs, TransactionType.CHARGE, TransactionType.REFUND, PAID_STATUSES);
        long paidCount = repository.countPaidCharges(tenantId, fromTs, toTs, TransactionType.CHARGE, PAID_STATUSES);
        long totalCount = repository.countTotal(tenantId, fromTs, toTs);
        long payingClients = repository.countPayingClients(tenantId, fromTs, toTs, PAID_STATUSES);
        return new PeriodAgg(
                nz(rev.getGrossRevenue()), nz(rev.getNetRevenue()),
                nz(rev.getTotalFees()), nz(rev.getTotalRefunds()),
                paidCount, totalCount, payingClients);
    }

    private static OffsetDateTime startOf(LocalDate date) {
        return date.atStartOfDay().atOffset(ZoneOffset.UTC);
    }

    private static OffsetDateTime endOf(LocalDate date) {
        return date.atTime(LocalTime.MAX).atOffset(ZoneOffset.UTC);
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static BigDecimal scale(BigDecimal v) {
        return nz(v).setScale(2, RoundingMode.HALF_UP);
    }

    /** Percentual de {@code part} sobre {@code total}, com 2 casas. */
    private static BigDecimal ratioPct(BigDecimal part, BigDecimal total) {
        if (total == null || total.signum() == 0) {
            return BigDecimal.ZERO;
        }
        return part.multiply(BigDecimal.valueOf(100)).divide(total, 2, RoundingMode.HALF_UP);
    }

    /** Crescimento percentual de {@code current} sobre {@code previous}. */
    private static BigDecimal growthPct(BigDecimal current, BigDecimal previous) {
        if (previous == null || previous.signum() == 0) {
            return current != null && current.signum() != 0 ? BigDecimal.valueOf(100) : BigDecimal.ZERO;
        }
        return current.subtract(previous).multiply(BigDecimal.valueOf(100))
                .divide(previous, 2, RoundingMode.HALF_UP);
    }

    private record PeriodAgg(
            BigDecimal grossRevenue,
            BigDecimal netRevenue,
            BigDecimal totalFees,
            BigDecimal totalRefunds,
            long paidCount,
            long totalCount,
            long payingClients) {
    }
}
