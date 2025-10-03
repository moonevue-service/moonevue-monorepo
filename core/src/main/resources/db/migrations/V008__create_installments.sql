CREATE TABLE installments (
    installment_id BIGSERIAL PRIMARY KEY,
    transaction_id BIGINT NOT NULL REFERENCES transactions(id) ON DELETE CASCADE,
    installment_number INT NOT NULL,
    total_installments INT NOT NULL,
    amount NUMERIC(18,2) NOT NULL,
    interest_amount NUMERIC(18,2) DEFAULT 0,
    due_date DATE NOT NULL,
    paid_date DATE,
    paid_amount NUMERIC(18,2),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    payment_method VARCHAR(50),
    payment_reference VARCHAR(200),
    overdue_days INT,
    overdue_fee NUMERIC(18,2) DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_installment_transaction ON installments(transaction_id);
CREATE INDEX idx_installment_due_date ON installments(due_date);
CREATE INDEX idx_installment_status ON installments(status);
CREATE INDEX idx_installment_number ON installments(transaction_id, installment_number);
