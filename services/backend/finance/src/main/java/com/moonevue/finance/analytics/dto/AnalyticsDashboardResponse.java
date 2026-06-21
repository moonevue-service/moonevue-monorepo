package com.moonevue.finance.analytics.dto;

import java.util.List;

/**
 * Payload consolidado do dashboard executivo: evita múltiplas chamadas do frontend.
 */
public record AnalyticsDashboardResponse(
        ExecutiveSummaryResponse summary,
        RevenueTimeSeriesResponse revenueTimeSeries,
        List<ClientRevenueResponse> topClients,
        List<StatusBreakdownResponse> statusBreakdown,
        ReceivablesResponse receivables,
        List<InsightResponse> insights
) {
}
