ALTER TABLE audit_logs 
ADD COLUMN IF NOT EXISTS action_timestamp TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP;

-- Copy existing timestamps if any
UPDATE audit_logs 
SET action_timestamp = modified_at 
WHERE action_timestamp IS NULL;