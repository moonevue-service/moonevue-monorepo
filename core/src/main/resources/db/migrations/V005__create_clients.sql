CREATE TABLE clients (
     client_id BIGSERIAL PRIMARY KEY,
    contractor_id BIGINT NOT NULL REFERENCES contractors(contractor_id),
    name VARCHAR(200) NOT NULL,
    cpf_cnpj VARCHAR(14) NOT NULL,
    email VARCHAR(100) NOT NULL,
    phone VARCHAR(20),
    address_street VARCHAR(200),
    address_number VARCHAR(20),
    address_complement VARCHAR(100),
    address_neighborhood VARCHAR(100),
    address_city VARCHAR(100),
    address_state VARCHAR(2),
    address_zip_code VARCHAR(10),
    address_country VARCHAR(50) DEFAULT 'Brasil',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT uk_contractor_client_cpf_cnpj UNIQUE(contractor_id, cpf_cnpj)
);

CREATE INDEX idx_client_contractor ON clients(contractor_id);
CREATE INDEX idx_client_cpf_cnpj ON clients(cpf_cnpj);
CREATE INDEX idx_client_email ON clients(email);
CREATE INDEX idx_client_status ON clients(status);