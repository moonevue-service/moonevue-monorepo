package com.moonevue.finance.analytics.dto;

import com.moonevue.core.enums.TransactionStatus;

import java.math.BigDecimal;

public record StatusBreakdownResponse(
        TransactionStatus status,
        long txCount,
        BigDecimal totalAmount,
        BigDecimal sharePct
) {
}
