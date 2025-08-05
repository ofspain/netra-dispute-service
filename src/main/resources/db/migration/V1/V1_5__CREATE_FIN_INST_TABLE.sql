CREATE TABLE financial_institutions (
   id BIGSERIAL PRIMARY KEY,
   created_at TIMESTAMP NOT NULL DEFAULT NOW(),
   updated_at TIMESTAMP DEFAULT NOW(),
   name VARCHAR(255) NOT NULL,
   code VARCHAR(100) NOT NULL,
   domain_code VARCHAR(50) NOT NULL,
   disabled BOOLEAN NOT NULL DEFAULT FALSE,
   endpoint_config REFERENCES endpoint_configs(id) ON DELETE CASCADE,

   UNIQUE (code),
   UNIQUE (domain_code)
);

-- Index for created date (e.g. for sorting, paging, analytics)
CREATE INDEX idx_fi_created_at ON financial_institutions(created_at);

-- Optional: If you query by domain_code frequently
CREATE INDEX idx_fi_domain_code ON financial_institutions(domain_code);
CREATE INDEX idx_fi_name ON financial_institutions(name);
CREATE INDEX idx_fi_code ON financial_institutions(code);

-- Optional: Index on disabled if filtering often
CREATE INDEX idx_fi_disabled ON financial_institutions(disabled);