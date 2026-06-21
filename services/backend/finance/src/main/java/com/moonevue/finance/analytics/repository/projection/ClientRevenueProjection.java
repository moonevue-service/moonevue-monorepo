package com.moonevue.finance.analytics.repository.projection;

import java.math.BigDecimal;

public interface ClientRevenueProjection {
    Long getClientId();

    String getClientName();

    BigDecimal getRevenue();

    Long getTxCount();
}
