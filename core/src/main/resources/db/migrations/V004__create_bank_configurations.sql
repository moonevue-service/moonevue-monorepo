CREATE TABLE bank_configurations (
    config_id BIGSERIAL PRIMARY KEY,
    contractor_id BIGINT NOT NULL REFERENCES contractors(contractor_id),
    bank_account_id BIGINT NOT NULL REFERENCES bank_accounts(id),
    api_key VARCHAR(500) NOT NULL,
    api_secret VARCHAR(500),
    environment VARCHAR(20) NOT NULL DEFAULT 'SANDBOX',
    extra_config JSONB NOT NULL DEFAULT '{}',
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    webhook_url VARCHAR(500),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE,
    last_sync_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT uk_contractor_bank_env UNIQUE (contractor_id, bank_account_id, environment)
);

CREATE INDEX idx_bank_config_contractor ON bank_configurations(contractor_id);
CREATE INDEX idx_bank_config_environment ON bank_configurations(environment);
CREATE INDEX idx_bank_config_active ON bank_configurations(is_active);