CREATE TABLE transaction_logs (
    log_id BIGSERIAL PRIMARY KEY,
    transaction_id BIGINT NOT NULL REFERENCES transactions(id) ON DELETE CASCADE,
    event_type VARCHAR(50) NOT NULL,
    message VARCHAR(1000) NOT NULL,
    severity VARCHAR(20) NOT NULL DEFAULT 'INFO',
    http_status_code INT,
    error_code VARCHAR(50),
    metadata JSONB NOT NULL DEFAULT '{}',
    ip_address VARCHAR(45),
    user_agent VARCHAR(500),
    event_date TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_tx_log_transaction ON transaction_logs(transaction_id);
CREATE INDEX idx_tx_log_event_date ON transaction_logs(event_date);
CREATE INDEX idx_tx_log_event_type ON transaction_logs(event_type);
CREATE INDEX idx_tx_log_severity ON transaction_logs(severity);
