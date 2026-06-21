package com.moonevue.finance.analytics.dto;

import com.moonevue.finance.analytics.domain.InsightSeverity;

import java.math.BigDecimal;

/**
 * Insight automático gerado a partir das regras de negócio sobre os agregados.
 */
public record InsightResponse(
        InsightSeverity severity,
        String category,
        String title,
        String message,
        BigDecimal metricValue
) {
}
