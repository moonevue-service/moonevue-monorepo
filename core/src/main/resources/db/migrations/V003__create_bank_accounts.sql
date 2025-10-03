CREATE TABLE bank_accounts (
    id BIGSERIAL PRIMARY KEY,
    owner VARCHAR(200) NOT NULL,
    number VARCHAR(100) NOT NULL UNIQUE,
    balance NUMERIC(18,2) NOT NULL,
    bank VARCHAR(100) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    user_id BIGINT NOT NULL REFERENCES "user"(id)
);
CREATE INDEX idx_account_owner ON bank_accounts(owner);