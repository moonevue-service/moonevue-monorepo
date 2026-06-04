-- V4: Consolidação de baseline — garante que audit_logs e action_timestamp
-- existam de forma idempotente em qualquer estado de migração anterior.
--
-- Histórico do problema:
--   V1: criou audit_logs sem IF NOT EXISTS — poderia falhar em banco existente.
--   V2: recriou a tabela com IF NOT EXISTS (ainda no repositório).
--   V3: adicionou a coluna action_timestamp (ainda no repositório).
--   V1: removido do repositório por ser a migration causadora da duplicidade.
--
-- Esta migration é no-op para ambientes que passaram por V2 + V3.
-- Ambientes criados do zero a partir de V2 são igualmente cobertos.

-- Garante tabela completa para bancos sem histórico anterior
CREATE TABLE IF NOT EXISTS audit_logs (
    id BIGSERIAL PRIMARY KEY,
    entity_id VARCHAR(255),
    entity_type VARCHAR(255),
    action VARCHAR(50),
    modified_by VARCHAR(255),
    modified_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    details JSONB,
    action_timestamp TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Garante coluna action_timestamp em tabelas criadas por versões anteriores sem ela
ALTER TABLE audit_logs ADD COLUMN IF NOT EXISTS action_timestamp TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP;
