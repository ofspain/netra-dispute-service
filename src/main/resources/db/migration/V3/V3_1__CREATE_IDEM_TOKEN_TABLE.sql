CREATE TABLE idempotency_tokens (
    id BIGSERIAL PRIMARY KEY,
    key VARCHAR(512) NOT NULL,  -- Composite key: "clientKey::operation"
    operation VARCHAR(100) NOT NULL,
    actor VARCHAR(100) NOT NULL,
    fingerprint VARCHAR(64) NOT NULL, -- SHA256 hash
    created_at TIMESTAMP NOT NULL,
    completed_at TIMESTAMP,
    result_type VARCHAR(20),

    -- Unique constraint on the composite key
    UNIQUE(key, operation)
);

-- Index for quick lookups
CREATE INDEX idx_idempotency_client_operation ON idempotency_tokens (key, operation);