ALTER TABLE transactions
    ADD COLUMN IF NOT EXISTS client_id BIGINT,
    ADD COLUMN IF NOT EXISTS checkout_access_mode VARCHAR(30) NOT NULL DEFAULT 'PUBLIC',
    ADD COLUMN IF NOT EXISTS checkout_identity_verified_at TIMESTAMPTZ;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.table_constraints
        WHERE constraint_name = 'fk_transactions_client'
    ) THEN
        ALTER TABLE transactions
            ADD CONSTRAINT fk_transactions_client
            FOREIGN KEY (client_id) REFERENCES clients(client_id);
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_tx_client_id ON transactions(client_id);
