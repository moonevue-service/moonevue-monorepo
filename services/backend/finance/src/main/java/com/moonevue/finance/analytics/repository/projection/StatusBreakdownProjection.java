package com.moonevue.finance.analytics.repository.projection;

import com.moonevue.core.enums.TransactionStatus;

import java.math.BigDecimal;

public interface StatusBreakdownProjection {
    TransactionStatus getStatus();

    Long getTxCount();

    BigDecimal getTotalAmount();
}
