CREATE TABLE IF NOT EXISTS webhook_events (
    id BIGSERIAL PRIMARY KEY,
    provider VARCHAR(40) NOT NULL,
    event_key VARCHAR(200) NOT NULL,
    payload TEXT NOT NULL,
    processed BOOLEAN NOT NULL DEFAULT FALSE,
    result VARCHAR(40),
    message VARCHAR(1000),
    processed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_webhook_provider_event_key UNIQUE (provider, event_key)
);

CREATE INDEX IF NOT EXISTS idx_webhook_provider ON webhook_events(provider);
CREATE INDEX IF NOT EXISTS idx_webhook_processed ON webhook_events(processed);
CREATE INDEX IF NOT EXISTS idx_webhook_created_at ON webhook_events(created_at);
