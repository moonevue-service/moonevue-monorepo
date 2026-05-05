ALTER TABLE transactions
    ADD COLUMN IF NOT EXISTS bank_configuration_id BIGINT REFERENCES bank_configurations(config_id),
    ADD COLUMN IF NOT EXISTS checkout_token UUID,
    ADD COLUMN IF NOT EXISTS checkout_expires_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS checkout_instrument VARCHAR(30),
    ADD COLUMN IF NOT EXISTS checkout_pix_key VARCHAR(255),
    ADD COLUMN IF NOT EXISTS payer_name VARCHAR(200),
    ADD COLUMN IF NOT EXISTS payer_email VARCHAR(200),
    ADD COLUMN IF NOT EXISTS payer_document VARCHAR(20),
    ADD COLUMN IF NOT EXISTS payer_phone VARCHAR(30),
    ADD COLUMN IF NOT EXISTS provider_payload JSONB,
    ADD COLUMN IF NOT EXISTS provider_response JSONB,
    ADD COLUMN IF NOT EXISTS paid_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS failure_reason VARCHAR(1000);

CREATE UNIQUE INDEX IF NOT EXISTS uk_transactions_checkout_token ON transactions(checkout_token) WHERE checkout_token IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_tx_checkout_status ON transactions(status);
CREATE INDEX IF NOT EXISTS idx_tx_checkout_expires_at ON transactions(checkout_expires_at);
