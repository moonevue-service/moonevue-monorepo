package com.moonevue.finance.analytics.dto;

import java.math.BigDecimal;

public record ClientRevenueResponse(
        int rank,
        Long clientId,
        String clientName,
        BigDecimal revenue,
        long txCount,
        BigDecimal sharePct
) {
}
