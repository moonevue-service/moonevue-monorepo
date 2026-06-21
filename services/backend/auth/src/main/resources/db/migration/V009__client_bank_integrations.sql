-- ============================================================================
-- V009: Integração bancária do cliente (multi-banco)
-- ----------------------------------------------------------------------------
-- Um mesmo cliente pode existir simultaneamente em vários provedores de
-- pagamento (EFI, ASAAS, Banco Inter, ...). Cada provedor pode ter o seu próprio
-- identificador para o cliente (customer id). Por isso o identificador bancário
-- NÃO deve viver na tabela `clients`, e sim numa tabela de ligação dedicada.
--
-- Regras:
--  * (client_id, bank_provider) é único — um registro por par cliente/provedor.
--  * bank_customer_id é NULL-able: provedores como a EFI não possuem um customer
--    id externo real; nesse caso guardamos um identificador interno sintético
--    (ex.: "efi:internal:<clientId>") para manter a mesma arquitetura.
--  * metadata (JSONB) guarda detalhes específicos do provedor sem poluir o core.
-- ============================================================================

CREATE TABLE IF NOT EXISTS client_bank_integrations (
    id                BIGSERIAL PRIMARY KEY,
    client_id         BIGINT NOT NULL REFERENCES clients(client_id) ON DELETE CASCADE,
    bank_provider     VARCHAR(40) NOT NULL,
    bank_customer_id  VARCHAR(255),
    metadata          JSONB,
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_client_bank_provider UNIQUE (client_id, bank_provider)
);

-- Busca reversa: "dado um provedor + customer id externo, qual o cliente?"
CREATE INDEX IF NOT EXISTS idx_cbi_provider_customer
    ON client_bank_integrations (bank_provider, bank_customer_id);

CREATE INDEX IF NOT EXISTS idx_cbi_client
    ON client_bank_integrations (client_id);
