package com.moonevue.finance.analytics.domain;

/**
 * Granularidade temporal das agregações analíticas.
 * O valor {@link #sql()} é usado como primeiro argumento de {@code date_trunc} no Postgres.
 */
public enum Granularity {
    DAY("day"),
    WEEK("week"),
    MONTH("month"),
    QUARTER("quarter"),
    YEAR("year");

    private final String sql;

    Granularity(String sql) {
        this.sql = sql;
    }

    public String sql() {
        return sql;
    }
}
