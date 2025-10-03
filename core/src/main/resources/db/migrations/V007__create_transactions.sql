CREATE TABLE transactions (
    id BIGSERIAL PRIMARY KEY,
    account_id BIGINT NOT NULL REFERENCES bank_accounts(id) ON DELETE CASCADE,
    subscription_id BIGINT REFERENCES subscriptions(subscription_id) ON DELETE SET NULL,
    amount NUMERIC(18,2) NOT NULL,
    type VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    description VARCHAR(500),
    external_reference VARCHAR(200),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_tx_account_id ON transactions(account_id);
CREATE INDEX idx_tx_created_at ON transactions(created_at);
CREATE INDEX idx_tx_subscription ON transactions(subscription_id);
CREATE INDEX idx_tx_status ON transactions(status);
