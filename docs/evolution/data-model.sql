-- Initial data model proposal for customer/transaction/charge evolution
-- Target: PostgreSQL 16+

CREATE EXTENSION IF NOT EXISTS pgcrypto;

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'party_type') THEN
    CREATE TYPE party_type AS ENUM ('INDIVIDUAL', 'COMPANY');
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'customer_status') THEN
    CREATE TYPE customer_status AS ENUM ('ACTIVE', 'INACTIVE', 'BLOCKED');
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'transaction_status') THEN
    CREATE TYPE transaction_status AS ENUM (
      'DRAFT',
      'READY_FOR_CHECKOUT',
      'AWAITING_CUSTOMER_INFO',
      'AWAITING_PAYMENT_METHOD',
      'PAYMENT_PROCESSING',
      'PAID',
      'OVERDUE',
      'CANCELED',
      'FAILED'
    );
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'charge_status') THEN
    CREATE TYPE charge_status AS ENUM (
      'NOT_CREATED',
      'CREATING',
      'CREATED',
      'AWAITING_PAYMENT',
      'PARTIALLY_PAID',
      'PAID',
      'EXPIRED',
      'CANCELED',
      'FAILED'
    );
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'payment_method') THEN
    CREATE TYPE payment_method AS ENUM ('PIX', 'BOLETO', 'CREDIT_CARD');
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'checkout_access_type') THEN
    CREATE TYPE checkout_access_type AS ENUM ('ANONYMOUS_CONTROLLED', 'CUSTOMER_AUTH_REQUIRED');
  END IF;
END $$;

CREATE TABLE IF NOT EXISTS customer (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id UUID NOT NULL,
  party_type party_type NOT NULL,
  status customer_status NOT NULL DEFAULT 'ACTIVE',
  legal_name VARCHAR(180) NOT NULL,
  trade_name VARCHAR(180),
  document_number VARCHAR(32) NOT NULL,
  document_number_normalized VARCHAR(32) NOT NULL,
  primary_email VARCHAR(190),
  primary_email_normalized VARCHAR(190),
  primary_phone VARCHAR(32),
  primary_phone_normalized VARCHAR(32),
  birth_or_opening_date DATE,
  notes TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  created_by UUID,
  updated_by UUID,
  deleted_at TIMESTAMPTZ
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_customer_tenant_document
  ON customer(tenant_id, document_number_normalized)
  WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS ix_customer_tenant_name
  ON customer(tenant_id, legal_name);

CREATE INDEX IF NOT EXISTS ix_customer_tenant_email
  ON customer(tenant_id, primary_email_normalized)
  WHERE primary_email_normalized IS NOT NULL;

CREATE INDEX IF NOT EXISTS ix_customer_tenant_phone
  ON customer(tenant_id, primary_phone_normalized)
  WHERE primary_phone_normalized IS NOT NULL;

CREATE TABLE IF NOT EXISTS customer_address (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  customer_id UUID NOT NULL REFERENCES customer(id),
  label VARCHAR(40) NOT NULL DEFAULT 'MAIN',
  zip_code VARCHAR(16),
  street VARCHAR(180),
  number VARCHAR(30),
  complement VARCHAR(120),
  district VARCHAR(120),
  city VARCHAR(120),
  state VARCHAR(40),
  country VARCHAR(2) DEFAULT 'BR',
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS ix_customer_address_customer ON customer_address(customer_id);

CREATE TABLE IF NOT EXISTS transaction_record (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id UUID NOT NULL,
  customer_id UUID REFERENCES customer(id),
  bank_account_id UUID NOT NULL,
  status transaction_status NOT NULL DEFAULT 'DRAFT',
  amount_principal NUMERIC(14,2) NOT NULL,
  currency_code CHAR(3) NOT NULL DEFAULT 'BRL',
  due_date DATE NOT NULL,
  description VARCHAR(240) NOT NULL,
  interest_type VARCHAR(20) NOT NULL DEFAULT 'PERCENTAGE',
  interest_value NUMERIC(10,4) NOT NULL DEFAULT 0,
  fine_type VARCHAR(20) NOT NULL DEFAULT 'PERCENTAGE',
  fine_value NUMERIC(10,4) NOT NULL DEFAULT 0,
  payment_method_policy VARCHAR(24) NOT NULL DEFAULT 'CUSTOMER_CHOOSES',
  source_channel VARCHAR(30) NOT NULL DEFAULT 'INTERNAL_PANEL',
  external_reference VARCHAR(80),
  checkout_expires_at TIMESTAMPTZ,
  allow_partial_payment BOOLEAN NOT NULL DEFAULT FALSE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  created_by UUID NOT NULL,
  updated_by UUID
);

CREATE INDEX IF NOT EXISTS ix_transaction_tenant_status
  ON transaction_record(tenant_id, status);

CREATE INDEX IF NOT EXISTS ix_transaction_tenant_customer
  ON transaction_record(tenant_id, customer_id);

CREATE INDEX IF NOT EXISTS ix_transaction_tenant_due_date
  ON transaction_record(tenant_id, due_date);

CREATE TABLE IF NOT EXISTS charge (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id UUID NOT NULL,
  transaction_id UUID NOT NULL REFERENCES transaction_record(id),
  provider VARCHAR(40) NOT NULL,
  provider_charge_id VARCHAR(120),
  provider_txid VARCHAR(120),
  payment_method payment_method NOT NULL,
  status charge_status NOT NULL DEFAULT 'NOT_CREATED',
  amount_total NUMERIC(14,2) NOT NULL,
  amount_paid NUMERIC(14,2) NOT NULL DEFAULT 0,
  provider_fee NUMERIC(14,2),
  net_amount NUMERIC(14,2),
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
  failure_message VARCHAR(240),
  raw_provider_payload JSONB,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS ix_charge_tenant_status
  ON charge(tenant_id, status);

CREATE INDEX IF NOT EXISTS ix_charge_tenant_transaction
  ON charge(tenant_id, transaction_id);

CREATE UNIQUE INDEX IF NOT EXISTS ux_charge_provider_id
  ON charge(provider, provider_charge_id)
  WHERE provider_charge_id IS NOT NULL;

CREATE TABLE IF NOT EXISTS checkout_token (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id UUID NOT NULL,
  transaction_id UUID NOT NULL REFERENCES transaction_record(id),
  token_hash VARCHAR(128) NOT NULL,
  access_type checkout_access_type NOT NULL,
  expires_at TIMESTAMPTZ NOT NULL,
  single_use BOOLEAN NOT NULL DEFAULT FALSE,
  max_attempts INT NOT NULL DEFAULT 10,
  current_attempts INT NOT NULL DEFAULT 0,
  revoked_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  last_used_at TIMESTAMPTZ
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_checkout_token_hash ON checkout_token(token_hash);
CREATE INDEX IF NOT EXISTS ix_checkout_token_transaction ON checkout_token(transaction_id);

CREATE TABLE IF NOT EXISTS employee (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id UUID NOT NULL,
  email VARCHAR(190) NOT NULL,
  email_normalized VARCHAR(190) NOT NULL,
  full_name VARCHAR(180) NOT NULL,
  active BOOLEAN NOT NULL DEFAULT TRUE,
  mfa_enabled BOOLEAN NOT NULL DEFAULT FALSE,
  last_login_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  created_by UUID,
  updated_by UUID
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_employee_tenant_email
  ON employee(tenant_id, email_normalized);

CREATE TABLE IF NOT EXISTS role (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id UUID,
  role_key VARCHAR(80) NOT NULL,
  display_name VARCHAR(120) NOT NULL,
  system_role BOOLEAN NOT NULL DEFAULT FALSE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_role_scope_key
  ON role(COALESCE(tenant_id, '00000000-0000-0000-0000-000000000000'::UUID), role_key);

CREATE TABLE IF NOT EXISTS permission (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  permission_key VARCHAR(120) NOT NULL UNIQUE,
  module_key VARCHAR(80) NOT NULL,
  action_key VARCHAR(80) NOT NULL,
  description VARCHAR(220)
);

CREATE TABLE IF NOT EXISTS role_permission (
  role_id UUID NOT NULL REFERENCES role(id),
  permission_id UUID NOT NULL REFERENCES permission(id),
  PRIMARY KEY(role_id, permission_id)
);

CREATE TABLE IF NOT EXISTS employee_role (
  employee_id UUID NOT NULL REFERENCES employee(id),
  role_id UUID NOT NULL REFERENCES role(id),
  PRIMARY KEY(employee_id, role_id)
);

CREATE TABLE IF NOT EXISTS audit_event (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id UUID,
  actor_type VARCHAR(20) NOT NULL,
  actor_id UUID,
  actor_email VARCHAR(190),
  action_key VARCHAR(120) NOT NULL,
  resource_type VARCHAR(80) NOT NULL,
  resource_id UUID,
  correlation_id VARCHAR(80),
  ip_address VARCHAR(80),
  user_agent VARCHAR(255),
  before_data JSONB,
  after_data JSONB,
  metadata JSONB,
  occurred_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS ix_audit_tenant_time ON audit_event(tenant_id, occurred_at DESC);

CREATE TABLE IF NOT EXISTS webhook_event_inbox (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  provider VARCHAR(40) NOT NULL,
  provider_event_id VARCHAR(160),
  event_type VARCHAR(120),
  payload_hash VARCHAR(128) NOT NULL,
  received_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  processed_at TIMESTAMPTZ,
  process_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
  process_attempts INT NOT NULL DEFAULT 0,
  last_error VARCHAR(255),
  raw_payload JSONB NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_webhook_event_unique
  ON webhook_event_inbox(provider, COALESCE(provider_event_id, payload_hash));

-- Basic check constraints
ALTER TABLE transaction_record
  ADD CONSTRAINT ck_transaction_amount_positive CHECK (amount_principal > 0);

ALTER TABLE charge
  ADD CONSTRAINT ck_charge_amount_non_negative CHECK (amount_total >= 0 AND amount_paid >= 0);

-- Helpful trigger-like pattern can be added in app layer for updated_at maintenance.
