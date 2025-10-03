CREATE TABLE subscriptions (
    subscription_id BIGSERIAL PRIMARY KEY,
    contractor_id BIGINT NOT NULL REFERENCES contractors(contractor_id) ON DELETE CASCADE,
    client_id BIGINT NOT NULL REFERENCES clients(client_id) ON DELETE CASCADE,
    value NUMERIC(18,2) NOT NULL,
    frequency VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    start_date DATE NOT NULL,
    end_date DATE,
    next_billing_date DATE,
    billing_day INT NOT NULL,
    description VARCHAR(500),
    auto_renew BOOLEAN NOT NULL DEFAULT TRUE,
    trial_days INT,
    trial_end_date DATE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE,
    canceled_at TIMESTAMP WITH TIME ZONE,
    cancellation_reason VARCHAR(500)
);

CREATE INDEX idx_subscription_contractor ON subscriptions(contractor_id);
CREATE INDEX idx_subscription_client ON subscriptions(client_id);
CREATE INDEX idx_subscription_status ON subscriptions(status);
CREATE INDEX idx_subscription_next_billing ON subscriptions(next_billing_date);
CREATE INDEX idx_subscription_dates ON subscriptions(start_date, end_date);
