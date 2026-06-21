package com.moonevue.finance.analytics.dto;

import com.moonevue.finance.analytics.domain.Granularity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record RevenueTimeSeriesResponse(
        Granularity granularity,
        List<Point> points
) {
    public record Point(
            LocalDate date,
            BigDecimal grossRevenue,
            BigDecimal netRevenue,
            long paidCount
    ) {
    }
}
