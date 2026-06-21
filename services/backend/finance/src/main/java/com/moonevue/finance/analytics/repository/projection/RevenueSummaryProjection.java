package com.moonevue.finance.analytics.repository.projection;

import java.math.BigDecimal;

/**
 * Agregados monetários de um período (uma única passada na tabela {@code transactions}).
 */
public interface RevenueSummaryProjection {
    BigDecimal getGrossRevenue();

    BigDecimal getNetRevenue();

    BigDecimal getTotalFees();

    BigDecimal getTotalRefunds();
}
