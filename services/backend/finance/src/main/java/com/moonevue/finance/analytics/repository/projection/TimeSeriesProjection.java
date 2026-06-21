package com.moonevue.finance.analytics.repository.projection;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Ponto de série temporal retornado pela query nativa com {@code date_trunc}.
 */
public interface TimeSeriesProjection {
    Instant getBucket();

    BigDecimal getGrossRevenue();

    BigDecimal getNetRevenue();

    Long getPaidCount();
}
