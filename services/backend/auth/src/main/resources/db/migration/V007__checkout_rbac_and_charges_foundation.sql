-- Foundation migration for checkout evolution, granular RBAC and charge tracking.
-- Compatible with current BIGINT-based schema.

-- -------------------------
-- Clients hardening
-- -------------------------
ALTER TABLE clients
    ADD COLUMN IF NOT EXISTS person_type VARCHAR(20) NOT NULL DEFAULT 'INDIVIDUAL',
    ADD COLUMN IF NOT EXISTS cpf_cnpj_normalized VARCHAR(20),
    ADD COLUMN IF NOT EXISTS email_normalized VARCHAR(120),
    ADD COLUMN IF NOT EXISTS phone_normalized VARCHAR(30);

UPDATE clients
SET cpf_cnpj_normalized = regexp_replace(cpf_cnpj, '[^0-9]', '', 'g')
WHERE cpf_cnpj_normalized IS NULL;

UPDATE clients
SET email_normalized = lower(email)
WHERE email_normalized IS NULL;

UPDATE clients
SET phone_normalized = regexp_replace(phone, '[^0-9]', '', 'g')
WHERE phone IS NOT NULL AND phone_normalized IS NULL;

CREATE INDEX IF NOT EXISTS idx_clients_tenant_cpf_cnpj_norm ON clients(tenant_id, cpf_cnpj_normalized);
CREATE INDEX IF NOT EXISTS idx_clients_tenant_email_norm ON clients(tenant_id, email_normalized);
CREATE INDEX IF NOT EXISTS idx_clients_tenant_phone_norm ON clients(tenant_id, phone_normalized);

-- -------------------------
-- Transaction lifecycle expansion
-- -------------------------
ALTER TABLE transactions
    ADD COLUMN IF NOT EXISTS due_date DATE,
    ADD COLUMN IF NOT EXISTS interest_type VARCHAR(20) NOT NULL DEFAULT 'PERCENTAGE',
    ADD COLUMN IF NOT EXISTS interest_value NUMERIC(10,4) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS fine_type VARCHAR(20) NOT NULL DEFAULT 'PERCENTAGE',
    ADD COLUMN IF NOT EXISTS fine_value NUMERIC(10,4) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS payment_method_policy VARCHAR(30) NOT NULL DEFAULT 'CUSTOMER_CHOOSES',
    ADD COLUMN IF NOT EXISTS source_channel VARCHAR(30) NOT NULL DEFAULT 'INTERNAL_PANEL',
    ADD COLUMN IF NOT EXISTS checkout_attempts INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS checkout_last_access_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS checkout_token_issued_at TIMESTAMPTZ;

UPDATE transactions
SET checkout_token_issued_at = created_at
WHERE checkout_token IS NOT NULL
  AND checkout_token_issued_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_tx_due_date ON transactions(due_date);
CREATE INDEX IF NOT EXISTS idx_tx_payment_policy ON transactions(payment_method_policy);

ALTER TABLE transactions
    ADD CONSTRAINT ck_tx_interest_value_non_negative CHECK (interest_value >= 0),
    ADD CONSTRAINT ck_tx_fine_value_non_negative CHECK (fine_value >= 0);

-- -------------------------
-- Charge records (internal x provider lifecycle)
-- -------------------------
CREATE TABLE IF NOT EXISTS charges (
    charge_id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    transaction_id BIGINT NOT NULL REFERENCES transactions(id) ON DELETE CASCADE,
    provider VARCHAR(40) NOT NULL,
    provider_charge_id VARCHAR(120),
    provider_txid VARCHAR(120),
    payment_method VARCHAR(20) NOT NULL,
    status VARCHAR(30) NOT NULL,
    amount_total NUMERIC(18,2) NOT NULL,
    amount_paid NUMERIC(18,2) NOT NULL DEFAULT 0,
    fee_amount NUMERIC(18,2),
    net_amount NUMERIC(18,2),
    due_date DATE,
    expires_at TIMESTAMPTZ,
    paid_at TIMESTAMPTZ,
    settlement_date DATE,
    pix_copy_paste TEXT,
    pix_qr_code_ref TEXT,
    boleto_line VARCHAR(255),
    boleto_pdf_ref TEXT,
    card_authorization_ref VARCHAR(120),
    reconciliation_state VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    failure_code VARCHAR(60),
    failure_message VARCHAR(1000),
    provider_payload JSONB,
    provider_response JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_charges_amount_non_negative CHECK (amount_total >= 0 AND amount_paid >= 0)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_charges_provider_charge_id ON charges(provider, provider_charge_id) WHERE provider_charge_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_charges_tenant_status ON charges(tenant_id, status);
CREATE INDEX IF NOT EXISTS idx_charges_transaction ON charges(transaction_id);
CREATE INDEX IF NOT EXISTS idx_charges_provider_txid ON charges(provider, provider_txid) WHERE provider_txid IS NOT NULL;

-- -------------------------
-- Checkout token ledger for link security and rotation
-- -------------------------
CREATE TABLE IF NOT EXISTS checkout_tokens (
    token_id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    transaction_id BIGINT NOT NULL REFERENCES transactions(id) ON DELETE CASCADE,
    token_uuid UUID NOT NULL,
    token_hash VARCHAR(128),
    access_mode VARCHAR(30) NOT NULL DEFAULT 'PUBLIC',
    expires_at TIMESTAMPTZ NOT NULL,
    single_use BOOLEAN NOT NULL DEFAULT FALSE,
    max_attempts INTEGER NOT NULL DEFAULT 10,
    current_attempts INTEGER NOT NULL DEFAULT 0,
    revoked_at TIMESTAMPTZ,
    last_used_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_checkout_tokens_uuid ON checkout_tokens(token_uuid);
CREATE INDEX IF NOT EXISTS idx_checkout_tokens_tx ON checkout_tokens(transaction_id);
CREATE INDEX IF NOT EXISTS idx_checkout_tokens_expires_at ON checkout_tokens(expires_at);

-- -------------------------
-- Granular RBAC (role x permission)
-- -------------------------
CREATE TABLE IF NOT EXISTS auth_permission (
    id BIGSERIAL PRIMARY KEY,
    permission_key VARCHAR(120) NOT NULL UNIQUE,
    module_key VARCHAR(80) NOT NULL,
    action_key VARCHAR(80) NOT NULL,
    description VARCHAR(255)
);

CREATE TABLE IF NOT EXISTS auth_role_permission (
    role_id BIGINT NOT NULL REFERENCES auth_role(id) ON DELETE CASCADE,
    permission_id BIGINT NOT NULL REFERENCES auth_permission(id) ON DELETE CASCADE,
    PRIMARY KEY(role_id, permission_id)
);

CREATE TABLE IF NOT EXISTS user_permission (
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    permission_id BIGINT NOT NULL REFERENCES auth_permission(id) ON DELETE CASCADE,
    granted BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY(user_id, permission_id)
);

INSERT INTO auth_permission (permission_key, module_key, action_key, description)
VALUES
    ('customers.read', 'customers', 'read', 'Listar e visualizar clientes'),
    ('customers.create', 'customers', 'create', 'Criar cliente'),
    ('customers.update', 'customers', 'update', 'Atualizar cliente'),
    ('customers.merge', 'customers', 'merge', 'Mesclar clientes duplicados'),
    ('transactions.read', 'transactions', 'read', 'Listar transacoes'),
    ('transactions.create', 'transactions', 'create', 'Criar transacao interna'),
    ('transactions.update', 'transactions', 'update', 'Atualizar transacao'),
    ('transactions.cancel', 'transactions', 'cancel', 'Cancelar transacao'),
    ('charges.read', 'charges', 'read', 'Ler cobrancas'),
    ('charges.emit', 'charges', 'emit', 'Emitir cobranca no banco'),
    ('charges.emit_immediate', 'charges', 'emit_immediate', 'Emitir cobranca no momento da criacao'),
    ('charges.retry', 'charges', 'retry', 'Reprocessar emissao de cobranca'),
    ('employees.read', 'employees', 'read', 'Listar funcionarios'),
    ('employees.create', 'employees', 'create', 'Adicionar funcionario'),
    ('employees.activate', 'employees', 'activate', 'Ativar funcionario'),
    ('employees.deactivate', 'employees', 'deactivate', 'Desativar funcionario'),
    ('roles.manage', 'rbac', 'manage', 'Gerenciar papeis e permissoes'),
    ('audit.read', 'audit', 'read', 'Ler trilha de auditoria'),
    ('webhooks.reprocess', 'webhooks', 'reprocess', 'Reprocessar eventos de webhook')
ON CONFLICT (permission_key) DO NOTHING;

-- Default permission mapping by existing role names.
INSERT INTO auth_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM auth_role r
JOIN auth_permission p ON p.permission_key IN (
    'customers.read',
    'customers.create',
    'customers.update',
    'customers.merge',
    'transactions.read',
    'transactions.create',
    'transactions.update',
    'transactions.cancel',
    'charges.read',
    'charges.emit',
    'charges.emit_immediate',
    'charges.retry',
    'employees.read',
    'employees.create',
    'employees.activate',
    'employees.deactivate',
    'roles.manage',
    'audit.read',
    'webhooks.reprocess'
)
WHERE r.name IN ('ADMIN', 'ADMIN_TENANT')
ON CONFLICT DO NOTHING;

INSERT INTO auth_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM auth_role r
JOIN auth_permission p ON p.permission_key IN (
    'customers.read',
    'customers.create',
    'customers.update',
    'transactions.read',
    'transactions.create',
    'transactions.update',
    'charges.read',
    'charges.emit',
    'charges.retry',
    'audit.read'
)
WHERE r.name = 'FINANCE'
ON CONFLICT DO NOTHING;

INSERT INTO auth_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM auth_role r
JOIN auth_permission p ON p.permission_key IN (
    'customers.read',
    'transactions.read',
    'charges.read'
)
WHERE r.name IN ('SUPPORT', 'EMPLOYED', 'USER')
ON CONFLICT DO NOTHING;
