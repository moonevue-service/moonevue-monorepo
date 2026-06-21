package com.moonevue.finance.analytics.dto;

import java.math.BigDecimal;

public record ReceivablesResponse(
        BigDecimal totalReceivable,
        BigDecimal totalOverdue,
        BigDecimal totalToDue,
        BigDecimal overdueRatioPct
) {
}
