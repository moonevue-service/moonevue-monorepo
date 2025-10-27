-- V1 - Initial schema for Banking Gateway (PostgreSQL)

-- Optional: ensure schema exists
CREATE SCHEMA IF NOT EXISTS public;

-- =========================
-- Table: contractors
-- =========================
CREATE TABLE public.contractors (
    contractor_id      BIGSERIAL PRIMARY KEY,
    name               VARCHAR(200) NOT NULL,
    business_name      VARCHAR(200),
    person_type        VARCHAR(2) NOT NULL,
    cpf_cnpj           VARCHAR(14) NOT NULL,
    contact_email      VARCHAR(100) NOT NULL,
    phone              VARCHAR(20),
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NULL,
    CONSTRAINT uk_contractor_cpf_cnpj UNIQUE (cpf_cnpj)
);

-- =========================
-- Table: clients
-- =========================
CREATE TABLE public.clients (
    client_id          BIGSERIAL PRIMARY KEY,
    contractor_id      BIGINT NOT NULL,
    name               VARCHAR(200) NOT NULL,
    cpf_cnpj           VARCHAR(14) NOT NULL,
    email              VARCHAR(100) NOT NULL,
    phone              VARCHAR(20),
    address_street     VARCHAR(200),
    address_number     VARCHAR(20),
    address_complement VARCHAR(100),
    address_neighborhood VARCHAR(100),
    address_city       VARCHAR(100),
    address_state      VARCHAR(2),
    address_zip_code   VARCHAR(10),
    address_country    VARCHAR(50) DEFAULT 'Brasil',
    status             VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NULL,
    CONSTRAINT fk_client_contractor
        FOREIGN KEY (contractor_id) REFERENCES public.contractors(contractor_id) ON DELETE CASCADE,
    CONSTRAINT uk_contractor_client_cpf_cnpj UNIQUE (contractor_id, cpf_cnpj)
);

CREATE INDEX idx_client_contractor ON public.clients (contractor_id);
CREATE INDEX idx_client_cpf_cnpj  ON public.clients (cpf_cnpj);
CREATE INDEX idx_client_email     ON public.clients (email);
CREATE INDEX idx_client_status    ON public.clients (status);

-- =========================
-- Table: bank_accounts
-- =========================
CREATE TABLE public.bank_accounts (
    bank_id            BIGSERIAL PRIMARY KEY,
    contractor_id      BIGINT NOT NULL,
    name               VARCHAR(200) NOT NULL,
    cd_agency          VARCHAR(100) NOT NULL,
    cd_account         VARCHAR(100) NOT NULL,
    cd_account_digit   VARCHAR(10),
    bank               VARCHAR(100) NOT NULL,
    account_type       VARCHAR(20),
    active             BOOLEAN NOT NULL DEFAULT TRUE,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_bank_account_contractor
        FOREIGN KEY (contractor_id) REFERENCES public.contractors(contractor_id) ON DELETE CASCADE,
    CONSTRAINT uk_contractor_account_unique UNIQUE (contractor_id, bank, cd_agency, cd_account, cd_account_digit)
);

CREATE INDEX idx_account_name        ON public.bank_accounts (name);
CREATE INDEX idx_account_contractor  ON public.bank_accounts (contractor_id);

-- =========================
-- Table: bank_configurations
-- =========================
CREATE TABLE public.bank_configurations (
    config_id          BIGSERIAL PRIMARY KEY,
    contractor_id      BIGINT NOT NULL,
    bank_account_id    BIGINT NOT NULL,
    environment        VARCHAR(20) NOT NULL DEFAULT 'SANDBOX',
    extra_config       JSONB NOT NULL DEFAULT '{}'::jsonb,
    is_active          BOOLEAN NOT NULL DEFAULT TRUE,
    webhook_url        VARCHAR(500),
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NULL,
    last_sync_at       TIMESTAMPTZ NULL,
    certificate_path   TEXT,
    certificate_password TEXT,
    CONSTRAINT fk_bank_config_contractor
        FOREIGN KEY (contractor_id) REFERENCES public.contractors(contractor_id) ON DELETE CASCADE,
    CONSTRAINT fk_bank_config_account
        FOREIGN KEY (bank_account_id) REFERENCES public.bank_accounts(bank_id) ON DELETE CASCADE,
    CONSTRAINT uk_contractor_bank_env UNIQUE (contractor_id, bank_account_id, environment)
);

CREATE INDEX idx_bank_config_contractor  ON public.bank_configurations (contractor_id);
CREATE INDEX idx_bank_config_environment ON public.bank_configurations (environment);
CREATE INDEX idx_bank_config_active      ON public.bank_configurations (is_active);

-- =========================
-- Table: subscriptions
-- =========================
CREATE TABLE public.subscriptions (
    subscription_id      BIGSERIAL PRIMARY KEY,
    contractor_id        BIGINT NOT NULL,
    client_id            BIGINT NOT NULL,
    value                NUMERIC(18,2) NOT NULL,
    frequency            VARCHAR(20) NOT NULL DEFAULT 'MONTHLY',
    status               VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    start_date           DATE NOT NULL,
    end_date             DATE,
    next_billing_date    DATE,
    billing_day          INTEGER NOT NULL,
    description          VARCHAR(500),
    auto_renew           BOOLEAN NOT NULL DEFAULT TRUE,
    trial_days           INTEGER,
    trial_end_date       DATE,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ NULL,
    canceled_at          TIMESTAMPTZ NULL,
    cancellation_reason  VARCHAR(500),
    CONSTRAINT fk_subscription_contractor
        FOREIGN KEY (contractor_id) REFERENCES public.contractors(contractor_id) ON DELETE CASCADE,
    CONSTRAINT fk_subscription_client
        FOREIGN KEY (client_id) REFERENCES public.clients(client_id) ON DELETE CASCADE
);

CREATE INDEX idx_subscription_contractor  ON public.subscriptions (contractor_id);
CREATE INDEX idx_subscription_client      ON public.subscriptions (client_id);
CREATE INDEX idx_subscription_status      ON public.subscriptions (status);
CREATE INDEX idx_subscription_dates       ON public.subscriptions (start_date, end_date);
CREATE INDEX idx_subscription_next_billing ON public.subscriptions (next_billing_date);

-- =========================
-- Table: transactions
-- =========================
CREATE TABLE public.transactions (
    id                 BIGSERIAL PRIMARY KEY,
    account_id         BIGINT NOT NULL,
    subscription_id    BIGINT NULL,
    amount             NUMERIC(18,2) NOT NULL,
    type               VARCHAR(20) NOT NULL DEFAULT 'CHARGE',
    status             VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    description        VARCHAR(500),
    external_reference VARCHAR(200),
    fee_amount         NUMERIC(18,2),
    net_amount         NUMERIC(18,2),
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NULL,
    CONSTRAINT fk_tx_account
        FOREIGN KEY (account_id) REFERENCES public.bank_accounts(bank_id) ON DELETE CASCADE,
    CONSTRAINT fk_tx_subscription
        FOREIGN KEY (subscription_id) REFERENCES public.subscriptions(subscription_id) ON DELETE SET NULL
);

CREATE INDEX idx_tx_account_id  ON public.transactions (account_id);
CREATE INDEX idx_tx_subscription ON public.transactions (subscription_id);
CREATE INDEX idx_tx_status      ON public.transactions (status);
CREATE INDEX idx_tx_created_at  ON public.transactions (created_at);

-- =========================
-- Table: installments
-- =========================
CREATE TABLE public.installments (
    installment_id      BIGSERIAL PRIMARY KEY,
    transaction_id      BIGINT NOT NULL,
    installment_number  INTEGER NOT NULL,
    total_installments  INTEGER NOT NULL,
    amount              NUMERIC(18,2) NOT NULL,
    interest_amount     NUMERIC(18,2) DEFAULT 0,
    due_date            DATE NOT NULL,
    paid_date           DATE,
    paid_amount         NUMERIC(18,2),
    status              VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    payment_method      VARCHAR(50),
    payment_reference   VARCHAR(200),
    overdue_days        INTEGER,
    overdue_fee         NUMERIC(18,2) DEFAULT 0,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NULL,
    CONSTRAINT fk_installment_tx
        FOREIGN KEY (transaction_id) REFERENCES public.transactions(id) ON DELETE CASCADE,
    CONSTRAINT uk_tx_installment_unique UNIQUE (transaction_id, installment_number)
);

CREATE INDEX idx_installment_number     ON public.installments (transaction_id, installment_number);
CREATE INDEX idx_installment_transaction ON public.installments (transaction_id);
CREATE INDEX idx_installment_due_date   ON public.installments (due_date);
CREATE INDEX idx_installment_status     ON public.installments (status);

-- =========================
-- Table: transaction_logs
-- =========================
CREATE TABLE public.transaction_logs (
    log_id            BIGSERIAL PRIMARY KEY,
    transaction_id    BIGINT NOT NULL,
    event_type        VARCHAR(50) NOT NULL,
    message           VARCHAR(1000) NOT NULL,
    severity          VARCHAR(20) NOT NULL DEFAULT 'INFO',
    http_status_code  INTEGER,
    error_code        VARCHAR(50),
    metadata          JSONB NOT NULL DEFAULT '{}'::jsonb,
    ip_address        VARCHAR(45),
    user_agent        VARCHAR(500),
    event_date        TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_tx_log_tx
        FOREIGN KEY (transaction_id) REFERENCES public.transactions(id) ON DELETE CASCADE
);

CREATE INDEX idx_tx_log_transaction ON public.transaction_logs (transaction_id);
CREATE INDEX idx_tx_log_event_type  ON public.transaction_logs (event_type);
CREATE INDEX idx_tx_log_severity    ON public.transaction_logs (severity);
CREATE INDEX idx_tx_log_event_date  ON public.transaction_logs (event_date);