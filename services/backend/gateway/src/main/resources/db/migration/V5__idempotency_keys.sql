-- Gateway operational table for idempotent write endpoints.
-- Note: flyway may be disabled by profile; this migration is prepared for activation.

CREATE TABLE IF NOT EXISTS idempotency_keys (
    id BIGSERIAL PRIMARY KEY,
    scope VARCHAR(120) NOT NULL,
    idem_key VARCHAR(200) NOT NULL,
    request_hash VARCHAR(128) NOT NULL,
    response_status INTEGER,
    response_body JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMPTZ
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_idempotency_scope_key
    ON idempotency_keys(scope, idem_key);

CREATE INDEX IF NOT EXISTS idx_idempotency_expires_at
    ON idempotency_keys(expires_at);
