package com.moonevue.finance.analytics.dto;

import com.moonevue.finance.analytics.domain.Granularity;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * KPIs executivos do período com comparação contra o período imediatamente anterior.
 */
public record ExecutiveSummaryResponse(
        Period period,
        BigDecimal grossRevenue,
        BigDecimal netRevenue,
        BigDecimal totalFees,
        BigDecimal totalRefunds,
        BigDecimal netProfit,
        BigDecimal averageTicket,
        BigDecimal contributionMarginPct,
        BigDecimal conversionRatePct,
        long paidTransactions,
        long totalTransactions,
        long payingClients,
        Growth growth
) {
    public record Period(LocalDate from, LocalDate to, Granularity granularity) {
    }

    public record Growth(
            BigDecimal grossRevenuePct,
            BigDecimal netRevenuePct,
            BigDecimal paidTransactionsPct
    ) {
    }
}
