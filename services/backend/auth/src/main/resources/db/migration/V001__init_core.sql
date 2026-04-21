-- Tabela Tenant
CREATE TABLE tenants (
                         id BIGSERIAL PRIMARY KEY,
                         name VARCHAR(255) NOT NULL,
                         document VARCHAR(255) NOT NULL UNIQUE,
                         active BOOLEAN NOT NULL DEFAULT TRUE,
                         created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
                         updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Tabela User
CREATE TABLE users (
                       id BIGSERIAL PRIMARY KEY,
                       tenant_id BIGINT NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
                       email VARCHAR(255) NOT NULL,
                       password_hash VARCHAR(255) NOT NULL,
                       enabled BOOLEAN NOT NULL DEFAULT TRUE,
                       created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
                       updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
                       CONSTRAINT uk_user_tenant_email UNIQUE (tenant_id, email)
);

-- Tabela Role
CREATE TABLE auth_role (
                           id BIGSERIAL PRIMARY KEY,
                           name VARCHAR(255) NOT NULL UNIQUE
);

-- Tabela User_Roles (ManyToMany)
CREATE TABLE user_roles (
                            user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                            role_id BIGINT NOT NULL REFERENCES auth_role(id) ON DELETE CASCADE,
                            PRIMARY KEY (user_id, role_id)
);

-- Tabela Session
CREATE TABLE auth_session (
                              id UUID PRIMARY KEY,
                              user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                              created_at TIMESTAMP WITH TIME ZONE NOT NULL,
                              last_seen_at TIMESTAMP WITH TIME ZONE NOT NULL,
                              expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
                              ip_address VARCHAR(255),
                              user_agent VARCHAR(255),
                              revoked BOOLEAN NOT NULL DEFAULT FALSE
);

-- Tabela BankAccount
CREATE TABLE bank_accounts (
                               bank_id BIGSERIAL PRIMARY KEY,
                               tenant_id BIGINT NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
                               name VARCHAR(200) NOT NULL,
                               cd_agency VARCHAR(100) NOT NULL,
                               cd_account VARCHAR(100) NOT NULL,
                               cd_account_digit VARCHAR(10),
                               bank VARCHAR(100) NOT NULL,
                               account_type VARCHAR(20),
                               active BOOLEAN NOT NULL DEFAULT TRUE,
                               created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
                               updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
                               CONSTRAINT uk_tenant_account_unique UNIQUE (tenant_id, bank, cd_agency, cd_account, cd_account_digit)
);

-- Tabela BankConfiguration
CREATE TABLE bank_configurations (
                                     config_id BIGSERIAL PRIMARY KEY,
                                     tenant_id BIGINT NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
                                     bank_account_id BIGINT NOT NULL REFERENCES bank_accounts(bank_id) ON DELETE CASCADE,
                                     environment VARCHAR(20) NOT NULL,
                                     extra_config JSONB NOT NULL DEFAULT '{}'::jsonb,
                                     is_active BOOLEAN NOT NULL DEFAULT TRUE,
                                     webhook_url VARCHAR(500),
                                     created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
                                     updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
                                     last_sync_at TIMESTAMP WITH TIME ZONE,
                                     certificate_path VARCHAR(255),
                                     certificate_password VARCHAR(255),
                                     CONSTRAINT uk_tenant_bank_env UNIQUE (tenant_id, bank_account_id, environment)
);

-- Tabela Client
CREATE TABLE clients (
                         client_id BIGSERIAL PRIMARY KEY,
                         tenant_id BIGINT NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
                         name VARCHAR(200) NOT NULL,
                         cpf_cnpj VARCHAR(14) NOT NULL,
                         email VARCHAR(100) NOT NULL,
                         phone VARCHAR(20),
    -- Address embutido (exemplo, ajuste conforme Address)
                         status VARCHAR(20) NOT NULL,
                         created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
                         updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
                         CONSTRAINT uk_tenant_client_cpf_cnpj UNIQUE (tenant_id, cpf_cnpj)
);

-- Tabela Transaction
CREATE TABLE transactions (
                              id BIGSERIAL PRIMARY KEY,
                              tenant_id BIGINT NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
                              account_id BIGINT NOT NULL REFERENCES bank_accounts(bank_id) ON DELETE CASCADE,
                              amount NUMERIC(18,2) NOT NULL,
                              type VARCHAR(20) NOT NULL,
                              status VARCHAR(20) NOT NULL,
                              description VARCHAR(500),
                              external_reference VARCHAR(200),
                              fee_amount NUMERIC(18,2),
                              net_amount NUMERIC(18,2),
                              created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
                              updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
    -- Adicione índices conforme necessário
);

-- Tabela TransactionLog
CREATE TABLE transaction_logs (
                                  log_id BIGSERIAL PRIMARY KEY,
                                  tenant_id BIGINT NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
                                  transaction_id BIGINT NOT NULL REFERENCES transactions(id) ON DELETE CASCADE,
                                  event_type VARCHAR(50) NOT NULL,
                                  message VARCHAR(1000) NOT NULL,
                                  severity VARCHAR(20) NOT NULL,
                                  http_status_code INTEGER,
                                  error_code VARCHAR(50),
                                  metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
                                  ip_address VARCHAR(45),
                                  user_agent VARCHAR(500),
                                  event_date TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
    -- Adicione índices conforme necessário
);
