package com.moonevue.finance.analytics.repository.projection;

import java.math.BigDecimal;

public interface ReceivablesProjection {
    BigDecimal getTotalReceivable();

    BigDecimal getTotalOverdue();

    BigDecimal getTotalToDue();
}
