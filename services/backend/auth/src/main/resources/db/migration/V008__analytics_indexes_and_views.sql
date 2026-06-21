-- Analytics: covering indexes + materialized view for revenue dashboards.
-- Read-only analytical layer on top of the Finance transactional data.
-- Idempotent (IF NOT EXISTS) to align with the existing migration style.

-- -------------------------
-- Covering indexes for analytical query patterns (tenant + period + status/type)
-- -------------------------
CREATE INDEX IF NOT EXISTS idx_tx_tenant_created
    ON transactions(tenant_id, created_at);

CREATE INDEX IF NOT EXISTS idx_tx_tenant_status_created
    ON transactions(tenant_id, status, created_at);

CREATE INDEX IF NOT EXISTS idx_tx_tenant_type_status_created
    ON transactions(tenant_id, type, status, created_at);

CREATE INDEX IF NOT EXISTS idx_tx_tenant_client_status
    ON transactions(tenant_id, client_id, status);

-- Partial index for open receivables (a receber / inadimplência)
CREATE INDEX IF NOT EXISTS idx_tx_open_receivables
    ON transactions(tenant_id, due_date)
    WHERE status IN ('PENDING', 'AUTHORIZED', 'PROCESSING');

-- -------------------------
-- Materialized view: daily revenue per tenant (heavy historical dashboards - Phase 2)
-- Refreshed by a scheduled job: REFRESH MATERIALIZED VIEW CONCURRENTLY mv_tx_daily_revenue;
-- -------------------------
CREATE MATERIALIZED VIEW IF NOT EXISTS mv_tx_daily_revenue AS
SELECT
    t.tenant_id                                                              AS tenant_id,
    date_trunc('day', t.created_at)                                          AS day,
    COALESCE(SUM(CASE WHEN t.type = 'CHARGE' AND t.status IN ('PAID', 'SETTLED', 'CAPTURED')
                      THEN t.amount ELSE 0 END), 0)                          AS gross_revenue,
    COALESCE(SUM(CASE WHEN t.type = 'CHARGE' AND t.status IN ('PAID', 'SETTLED', 'CAPTURED')
                      THEN t.net_amount ELSE 0 END), 0)                      AS net_revenue,
    COALESCE(SUM(CASE WHEN t.status IN ('PAID', 'SETTLED', 'CAPTURED')
                      THEN t.fee_amount ELSE 0 END), 0)                      AS fees,
    COALESCE(SUM(CASE WHEN t.type = 'CHARGE' AND t.status IN ('PAID', 'SETTLED', 'CAPTURED')
                      THEN 1 ELSE 0 END), 0)                                 AS paid_count
FROM transactions t
GROUP BY t.tenant_id, date_trunc('day', t.created_at)
WITH NO DATA;

-- Unique index required for REFRESH MATERIALIZED VIEW CONCURRENTLY
CREATE UNIQUE INDEX IF NOT EXISTS uk_mv_tx_daily_revenue
    ON mv_tx_daily_revenue(tenant_id, day);
