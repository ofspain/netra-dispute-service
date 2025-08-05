CREATE TABLE endpoint_configs (
   id BIGSERIAL PRIMARY KEY,                                -- Preferred over UUID
   created_at TIMESTAMP NOT NULL DEFAULT NOW(),             -- BaseEntity fields
   updated_at TIMESTAMP DEFAULT NOW(),

    -- Basic identity
   domain_code VARCHAR(100) NOT NULL UNIQUE,
   domain_type VARCHAR(50) NOT NULL,
   description TEXT,

    -- Core communication setup
   base_url TEXT NOT NULL,
   timeout_millis INT NOT NULL DEFAULT 5000,
   use_proxy BOOLEAN NOT NULL DEFAULT false,

    -- Proxy
   proxy_config JSONB,

    -- Auth
   requires_auth BOOLEAN NOT NULL DEFAULT false,
   auth_type VARCHAR(50) NOT NULL DEFAULT 'NONE',

    -- Request template
   request_body_template TEXT,

    -- Endpoint detail
   unique_transaction JSONB,
   multiple_transaction JSONB,

    -- Resilience registry
   retry_config JSONB,
   fallback_config JSONB
);

CREATE INDEX idx_endpoint_config_domain_code ON endpoint_configs(domain_code);
CREATE INDEX idx_endpoint_config_domain_type ON endpoint_configs(domain_type);
CREATE INDEX idx_endpoint_config_auth_type ON endpoint_configs(auth_type);

CREATE INDEX idx_endpoint_config_proxy_config_gin ON endpoint_configs USING GIN (proxy_config);
CREATE INDEX idx_endpoint_config_unique_txn_gin ON endpoint_configs USING GIN (unique_transaction);
CREATE INDEX idx_endpoint_config_multiple_txn_gin ON endpoint_configs USING GIN (multiple_transaction);
