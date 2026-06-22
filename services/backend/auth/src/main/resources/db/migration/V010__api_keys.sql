-- ============================================================================
-- V010: API Keys — credenciais de integração programática por tenant.
-- ----------------------------------------------------------------------------
-- A emissão de cobranças via API pública (gateway) é autenticada por API Keys.
-- No ambiente dev/compartilhado o schema é gerido exclusivamente pelo Flyway do
-- módulo `auth`, portanto a tabela precisa existir aqui (o `gateway` mantém uma
-- migração equivalente, idempotente, para cenários em que ele é dono do banco).
--
-- Formato da chave (apenas o hash é persistido): mvk_<live|test>_<keyId>_<secret>
--  * key_id      → identificador público (10 chars), único.
--  * secret_hash → HMAC-SHA-256 do segredo (nunca o segredo em claro).
--  * scopes      → separados por vírgula (ex.: "charges:write,charges:read").
-- ============================================================================

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
