CREATE TABLE audit_logs (
    id BIGSERIAL PRIMARY KEY,
    entity_id VARCHAR(255),
    entity_type VARCHAR(255),
    action VARCHAR(50),
    modified_by VARCHAR(255),
    modified_at TIMESTAMP WITH TIME ZONE DEFAULT now(),
    details JSONB
);