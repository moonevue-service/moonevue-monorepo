-- ============================================================================
-- V011: Atribuição de cobranças à API Key de origem (analytics de integrações).
-- ----------------------------------------------------------------------------
-- Permite medir a utilização das API Keys: cada cobrança criada via API pública
-- (gateway, /api/v1/charges) passa a registrar a chave que a originou.
--
--  * api_key_id     → FK lógica para api_keys(api_key_id). Nulo para cobranças
--                     criadas pelo painel interno.
--  * source_channel → já existente (V007); 'PUBLIC_API' para cobranças via API,
--                     'INTERNAL_PANEL' (default) para o painel.
-- ============================================================================

ALTER TABLE transactions
    ADD COLUMN IF NOT EXISTS api_key_id BIGINT;

CREATE INDEX IF NOT EXISTS idx_tx_api_key
    ON transactions(api_key_id)
    WHERE api_key_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_tx_tenant_source_created
    ON transactions(tenant_id, source_channel, created_at);
