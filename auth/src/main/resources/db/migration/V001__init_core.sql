-- Initial schema for multi-tenant financial system with banking gateway
-- PostgreSQL

BEGIN;

-- =============== Tenants ===============
CREATE TABLE tenants (
                         id               BIGSERIAL PRIMARY KEY,
                         name             VARCHAR(255) NOT NULL,
                         document         VARCHAR(255) NOT NULL,
                         active           BOOLEAN NOT NULL DEFAULT TRUE,
                         created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                         updated_at       TIMESTAMPTZ,
                         CONSTRAINT uk_tenant_document UNIQUE (document)
);

-- =============== Roles (auth_role) ===============
CREATE TABLE auth_role (
                           id    BIGSERIAL PRIMARY KEY,
                           name  VARCHAR(255) NOT NULL UNIQUE
);

INSERT INTO auth_role (name) VALUES
                                 ('ROLE_USER'),
                                 ('ROLE_TENANT_ADMIN'),
                                 ('ROLE_EMPLOYEE'),
                                 ('ROLE_ADMIN')
ON CONFLICT (name) DO NOTHING;

-- =============== Users (email globalmente único, case-insensitive) ===============
CREATE TABLE users (
                       id             BIGSERIAL PRIMARY KEY,
                       tenant_id      BIGINT NOT NULL,
                       email          VARCHAR(255) NOT NULL,
                       password_hash  VARCHAR(255) NOT NULL,
                       enabled        BOOLEAN NOT NULL DEFAULT TRUE,
                       created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                       updated_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                       CONSTRAINT fk_user_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE CASCADE
);
CREATE INDEX idx_user_tenant ON users(tenant_id);
-- Unicidade global por email (case-insensitive)
CREATE UNIQUE INDEX uk_users_email_lower ON users ((LOWER(email)));

-- =============== User Roles (join) ===============
CREATE TABLE user_roles (
                            user_id BIGINT NOT NULL,
                            role_id BIGINT NOT NULL,
                            PRIMARY KEY (user_id, role_id),
                            CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
                            CONSTRAINT fk_user_roles_role FOREIGN KEY (role_id) REFERENCES auth_role(id) ON DELETE CASCADE
);

-- =============== Sessions (auth_session) ===============
CREATE TABLE auth_session (
                              id            UUID PRIMARY KEY,
                              user_id       BIGINT NOT NULL,
                              created_at    TIMESTAMPTZ NOT NULL,
                              last_seen_at  TIMESTAMPTZ NOT NULL,
                              expires_at    TIMESTAMPTZ NOT NULL,
                              ip_address    VARCHAR(45),
                              user_agent    VARCHAR(500),
                              revoked       BOOLEAN NOT NULL DEFAULT FALSE,
                              CONSTRAINT fk_session_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
CREATE INDEX idx_session_user ON auth_session(user_id);
CREATE INDEX idx_session_expires_at ON auth_session(expires_at);

-- =============== Clients ===============
CREATE TABLE clients (
                         client_id        BIGSERIAL PRIMARY KEY,
                         tenant_id        BIGINT NOT NULL,
                         name             VARCHAR(200) NOT NULL,
                         cpf_cnpj         VARCHAR(14) NOT NULL,
                         email            VARCHAR(100) NOT NULL,
                         phone            VARCHAR(20),
    -- Embedded Address
                         address_street       VARCHAR(200),
                         address_number       VARCHAR(50),
                         address_complement   VARCHAR(100),
                         address_district     VARCHAR(100),
                         address_city         VARCHAR(100),
                         address_state        VARCHAR(50),
                         address_zip_code     VARCHAR(20),
                         address_country      VARCHAR(100),
                         status           VARCHAR(20) NOT NULL,
                         created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                         updated_at       TIMESTAMPTZ,
                         CONSTRAINT fk_client_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE CASCADE,
                         CONSTRAINT uk_tenant_client_cpf_cnpj UNIQUE (tenant_id, cpf_cnpj)
);
CREATE INDEX idx_client_tenant ON clients(tenant_id);
CREATE INDEX idx_client_email ON clients(email);
CREATE INDEX idx_client_name ON clients(name);

-- =============== Bank Accounts ===============
CREATE TABLE bank_accounts (
                               bank_id          BIGSERIAL PRIMARY KEY,
                               tenant_id        BIGINT NOT NULL,
                               name             VARCHAR(200) NOT NULL,
                               cd_agency        VARCHAR(100) NOT NULL,
                               cd_account       VARCHAR(100) NOT NULL,
                               cd_account_digit VARCHAR(10),
                               bank             VARCHAR(100) NOT NULL,
                               account_type     VARCHAR(20),
                               active           BOOLEAN NOT NULL DEFAULT TRUE,
                               created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                               updated_at       TIMESTAMPTZ,
                               CONSTRAINT fk_bank_account_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE CASCADE,
                               CONSTRAINT uk_tenant_account_unique UNIQUE (tenant_id, bank, cd_agency, cd_account, cd_account_digit)
);
CREATE INDEX idx_account_tenant ON bank_accounts(tenant_id);

-- =============== Bank Configurations ===============
CREATE TABLE bank_configurations (
                                     config_id        BIGSERIAL PRIMARY KEY,
                                     tenant_id        BIGINT NOT NULL,
                                     bank_account_id  BIGINT NOT NULL,
                                     environment      VARCHAR(20) NOT NULL,
                                     extra_config     JSONB NOT NULL DEFAULT '{}'::jsonb,
                                     is_active        BOOLEAN NOT NULL DEFAULT TRUE,
                                     webhook_url      VARCHAR(500),
                                     created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                                     updated_at       TIMESTAMPTZ,
                                     last_sync_at     TIMESTAMPTZ,
                                     certificate_path TEXT,
                                     certificate_password TEXT,
                                     CONSTRAINT fk_bank_configuration_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE CASCADE,
                                     CONSTRAINT fk_bank_configuration_account FOREIGN KEY (bank_account_id) REFERENCES bank_accounts(bank_id) ON DELETE CASCADE,
                                     CONSTRAINT uk_tenant_bank_env UNIQUE (tenant_id, bank_account_id, environment)
);
CREATE INDEX idx_bank_config_tenant ON bank_configurations(tenant_id);
CREATE INDEX idx_bank_config_account ON bank_configurations(bank_account_id);

-- =============== Subscriptions ===============
CREATE TABLE subscriptions (
                               subscription_id   BIGSERIAL PRIMARY KEY,
                               tenant_id         BIGINT NOT NULL,
                               client_id         BIGINT NOT NULL,
                               account_id        BIGINT NOT NULL,
                               value             NUMERIC(18,2) NOT NULL,
                               frequency         VARCHAR(20) NOT NULL,
                               status            VARCHAR(20) NOT NULL,
                               start_date        DATE NOT NULL,
                               end_date          DATE,
                               next_billing_date DATE,
                               billing_day       INT NOT NULL,
                               description       VARCHAR(500),
                               auto_renew        BOOLEAN NOT NULL DEFAULT TRUE,
                               trial_days        INT,
                               trial_end_date    DATE,
                               created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                               updated_at        TIMESTAMPTZ,
                               canceled_at       TIMESTAMPTZ,
                               cancellation_reason VARCHAR(500),
                               CONSTRAINT fk_subscription_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE CASCADE,
                               CONSTRAINT fk_subscription_client FOREIGN KEY (client_id) REFERENCES clients(client_id) ON DELETE CASCADE,
                               CONSTRAINT fk_subscription_account FOREIGN KEY (account_id) REFERENCES bank_accounts(bank_id) ON DELETE CASCADE
);
CREATE INDEX idx_subscription_tenant ON subscriptions(tenant_id);
CREATE INDEX idx_subscription_client ON subscriptions(client_id);
CREATE INDEX idx_subscription_status ON subscriptions(status);
CREATE INDEX idx_subscription_dates ON subscriptions(start_date, end_date);
CREATE INDEX idx_subscription_next_billing ON subscriptions(next_billing_date);

-- =============== Invoices ===============
CREATE TABLE invoices (
                          id               BIGSERIAL PRIMARY KEY,
                          uuid             UUID NOT NULL UNIQUE,
                          tenant_id        BIGINT NOT NULL,
                          client_id        BIGINT NOT NULL,
                          subscription_id  BIGINT,
                          status           VARCHAR(50) NOT NULL,
                          amount           NUMERIC(18,2) NOT NULL,
                          due_date         DATE NOT NULL,
                          paid_at          TIMESTAMPTZ,
                          canceled_at      TIMESTAMPTZ,
                          created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                          updated_at       TIMESTAMPTZ,
                          CONSTRAINT fk_invoice_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE CASCADE,
                          CONSTRAINT fk_invoice_client FOREIGN KEY (client_id) REFERENCES clients(client_id) ON DELETE CASCADE,
                          CONSTRAINT fk_invoice_subscription FOREIGN KEY (subscription_id) REFERENCES subscriptions(subscription_id) ON DELETE SET NULL
);
CREATE INDEX idx_invoice_tenant ON invoices(tenant_id);
CREATE INDEX idx_invoice_client ON invoices(client_id);
CREATE INDEX idx_invoice_status ON invoices(status);
CREATE INDEX idx_invoice_due_date ON invoices(due_date);

-- =============== Invoice Items ===============
CREATE TABLE invoice_items (
                               id             BIGSERIAL PRIMARY KEY,
                               invoice_id     BIGINT NOT NULL,
                               description    VARCHAR(255) NOT NULL,
                               quantity       INT NOT NULL,
                               unit_price     NUMERIC(18,2) NOT NULL,
                               total_amount   NUMERIC(18,2) NOT NULL,
                               CONSTRAINT fk_invoice_item_invoice FOREIGN KEY (invoice_id) REFERENCES invoices(id) ON DELETE CASCADE
);
CREATE INDEX idx_invoice_items_invoice ON invoice_items(invoice_id);

-- =============== Transactions ===============
CREATE TABLE transactions (
                              id                 BIGSERIAL PRIMARY KEY,
                              tenant_id          BIGINT NOT NULL,
                              account_id         BIGINT NOT NULL,
                              subscription_id    BIGINT,
                              invoice_id         BIGINT UNIQUE, -- 1:1 com invoice (opcional)
                              amount             NUMERIC(18,2) NOT NULL,
                              type               VARCHAR(20) NOT NULL,
                              status             VARCHAR(20) NOT NULL,
                              description        VARCHAR(500),
                              external_reference VARCHAR(200),
                              fee_amount         NUMERIC(18,2),
                              net_amount         NUMERIC(18,2),
                              created_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                              updated_at         TIMESTAMPTZ,
                              CONSTRAINT fk_transaction_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE CASCADE,
                              CONSTRAINT fk_transaction_account FOREIGN KEY (account_id) REFERENCES bank_accounts(bank_id) ON DELETE CASCADE,
                              CONSTRAINT fk_transaction_subscription FOREIGN KEY (subscription_id) REFERENCES subscriptions(subscription_id) ON DELETE SET NULL,
                              CONSTRAINT fk_transaction_invoice FOREIGN KEY (invoice_id) REFERENCES invoices(id) ON DELETE SET NULL
);
CREATE INDEX idx_tx_tenant ON transactions(tenant_id);
CREATE INDEX idx_tx_account_id ON transactions(account_id);
CREATE INDEX idx_tx_subscription ON transactions(subscription_id);
CREATE INDEX idx_tx_status ON transactions(status);
CREATE INDEX idx_tx_created_at ON transactions(created_at);

-- =============== Installments ===============
CREATE TABLE installments (
                              installment_id      BIGSERIAL PRIMARY KEY,
                              tenant_id           BIGINT NOT NULL,
                              transaction_id      BIGINT NOT NULL,
                              installment_number  INT NOT NULL,
                              total_installments  INT NOT NULL,
                              amount              NUMERIC(18,2) NOT NULL,
                              interest_amount     NUMERIC(18,2),
                              due_date            DATE NOT NULL,
                              paid_date           DATE,
                              paid_amount         NUMERIC(18,2),
                              status              VARCHAR(20) NOT NULL,
                              payment_method      VARCHAR(50),
                              payment_reference   VARCHAR(200),
                              overdue_days        INT,
                              overdue_fee         NUMERIC(18,2),
                              created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                              updated_at          TIMESTAMPTZ,
                              CONSTRAINT fk_installment_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE CASCADE,
                              CONSTRAINT fk_installment_transaction FOREIGN KEY (transaction_id) REFERENCES transactions(id) ON DELETE CASCADE,
                              CONSTRAINT uk_tx_installment_unique UNIQUE (transaction_id, installment_number)
);
CREATE INDEX idx_installment_tenant ON installments(tenant_id);
CREATE INDEX idx_installment_number ON installments(transaction_id, installment_number);
CREATE INDEX idx_installment_transaction ON installments(transaction_id);
CREATE INDEX idx_installment_due_date ON installments(due_date);
CREATE INDEX idx_installment_status ON installments(status);

-- =============== Transaction Logs ===============
CREATE TABLE transaction_logs (
                                  log_id           BIGSERIAL PRIMARY KEY,
                                  tenant_id        BIGINT NOT NULL,
                                  transaction_id   BIGINT NOT NULL,
                                  event_type       VARCHAR(50) NOT NULL,
                                  message          VARCHAR(1000) NOT NULL,
                                  severity         VARCHAR(20) NOT NULL,
                                  http_status_code INT,
                                  error_code       VARCHAR(50),
                                  metadata         JSONB NOT NULL DEFAULT '{}'::jsonb,
                                  ip_address       VARCHAR(45),
                                  user_agent       VARCHAR(500),
                                  event_date       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                                  CONSTRAINT fk_tx_log_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE CASCADE,
                                  CONSTRAINT fk_tx_log_transaction FOREIGN KEY (transaction_id) REFERENCES transactions(id) ON DELETE CASCADE
);
CREATE INDEX idx_tx_log_tenant ON transaction_logs(tenant_id);
CREATE INDEX idx_tx_log_transaction ON transaction_logs(transaction_id);
CREATE INDEX idx_tx_log_event_type ON transaction_logs(event_type);
CREATE INDEX idx_tx_log_severity ON transaction_logs(severity);
CREATE INDEX idx_tx_log_event_date ON transaction_logs(event_date);

-- =============== Audit Logs ===============
CREATE TABLE audit_logs (
                            id               BIGSERIAL PRIMARY KEY,
                            tenant_id        BIGINT,
                            user_id          BIGINT,
                            action_type      VARCHAR(50) NOT NULL,
                            entity_name      VARCHAR(50) NOT NULL,
                            entity_id        VARCHAR(255) NOT NULL,
                            changes          JSONB,
                            action_timestamp TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                            ip_address       VARCHAR(45),
                            CONSTRAINT fk_audit_log_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE CASCADE,
                            CONSTRAINT fk_audit_log_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL
);
CREATE INDEX idx_audit_tenant_user ON audit_logs(tenant_id, user_id);
CREATE INDEX idx_audit_entity ON audit_logs(entity_name, entity_id);

-- =============== Gateway API Logs ===============
CREATE TABLE gateway_api_logs (
                                  id                      BIGSERIAL PRIMARY KEY,
                                  correlation_id          UUID NOT NULL,
                                  tenant_id               BIGINT,
                                  bank_configuration_id   BIGINT,
                                  request_url             TEXT NOT NULL,
                                  request_method          VARCHAR(10) NOT NULL,
                                  request_headers         JSONB,
                                  request_body            TEXT,
                                  response_status_code    INT,
                                  response_headers        JSONB,
                                  response_body           TEXT,
                                  duration_ms             BIGINT,
                                  created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                                  CONSTRAINT fk_gateway_log_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE CASCADE,
                                  CONSTRAINT fk_gateway_log_bank_config FOREIGN KEY (bank_configuration_id) REFERENCES bank_configurations(config_id) ON DELETE SET NULL
);
CREATE INDEX idx_gateway_log_tenant ON gateway_api_logs(tenant_id);
CREATE INDEX idx_gateway_log_correlation ON gateway_api_logs(correlation_id);

COMMIT;