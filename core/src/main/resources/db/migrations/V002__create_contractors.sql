CREATE TABLE contractors (
    contractor_id BIGSERIAL PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    business_name VARCHAR(200),
    type VARCHAR(2) NOT NULL,
    cpf_cnpj VARCHAR(14) NOT NULL UNIQUE,
    contact_email VARCHAR(100) NOT NULL,
    phone VARCHAR(20),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE
);