-- API Keys: credenciais de integração programática por tenant.
-- Owner: gateway. Aplicada via Flyway no perfil prod (baseline-on-migrate).
-- Idempotente (IF NOT EXISTS) para conviver com bases já existentes.

CREATE TABLE IF NOT EXISTS api_keys (
    api_key_id   BIGSERIAL PRIMARY KEY,
    tenant_id    BIGINT NOT NULL REFERENCES tenants(id),
    key_id       VARCHAR(32) NOT NULL,
    secret_hash  VARCHAR(255) NOT NULL,
    name         VARCHAR(120) NOT NULL,
    environment  VARCHAR(10) NOT NULL,            -- LIVE | TEST
    scopes       TEXT NOT NULL DEFAULT '',        -- separados por vírgula
    status       VARCHAR(20) NOT NULL DEFAULT 'ACTIVE', -- ACTIVE | REVOKED
    last_used_at TIMESTAMPTZ,
    expires_at   TIMESTAMPTZ,
    created_by   BIGINT,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    revoked_at   TIMESTAMPTZ,
    revoked_by   BIGINT
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_api_keys_key_id ON api_keys(key_id);
CREATE INDEX IF NOT EXISTS idx_api_keys_tenant ON api_keys(tenant_id);
CREATE INDEX IF NOT EXISTS idx_api_keys_status ON api_keys(status);
