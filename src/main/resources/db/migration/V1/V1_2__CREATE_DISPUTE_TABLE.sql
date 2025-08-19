-- Dispute table
CREATE TABLE disputes (
                          id BIGSERIAL PRIMARY KEY,
                          created_at TIMESTAMP NOT NULL DEFAULT NOW(),
                          updated_at TIMESTAMP DEFAULT NOW(),
                          transaction_id BIGINT NOT NULL REFERENCES transactions(id) ON DELETE CASCADE,
                          dispute_marked_legit_time TIMESTAMP,
                          current_state VARCHAR(50) NOT NULL,
                          previous_state VARCHAR(50),
                          created_via VARCHAR(50) NOT NULL,
                          log_code VARCHAR(80),

                          disputant_identity_uuid VARCHAR(100) NOT NULL,
                          disputant_type VARCHAR(50) NOT NULL CHECK (
                              disputant_type IN ('CUSTOMERUSER', 'INSTITUTIONUSER', 'INTERNALUSER')
                              ),
                          disputant_domain_code VARCHAR(50) NOT NULL,   -- ✅ fixed

                          note TEXT,
                          issuer_code VARCHAR(50),
                          acquirer_code VARCHAR(50),
                          merchant_code VARCHAR(50),
                          beneficiary_code VARCHAR(50),
                          switcher_code VARCHAR(50),
                          locked BOOLEAN NOT NULL DEFAULT FALSE,
                          is_finalized BOOLEAN NOT NULL DEFAULT FALSE,
                          is_resolved BOOLEAN NOT NULL DEFAULT FALSE,
                          resolved_in_customer_favor BOOLEAN NOT NULL DEFAULT FALSE
);

-- Index on created_at for chronological queries
CREATE INDEX idx_dispute_created_at ON disputes(created_at);

-- Index on updated_at for syncing or auditing
CREATE INDEX idx_dispute_updated_at ON disputes(updated_at);

CREATE INDEX idx_dispute_logcode ON disputes(log_code);

-- Index on dispute_marked_legit_time to quickly find legit-marked disputes
CREATE INDEX idx_dispute_legit_time ON disputes(dispute_marked_legit_time);

-- Optional: Index on transaction_id for joins
CREATE INDEX idx_dispute_transaction_id ON disputes(transaction_id);

-- Optional: Indexes on flags (if filtering a lot)
CREATE INDEX idx_dispute_finalized ON disputes(is_finalized);
CREATE INDEX idx_dispute_locked ON disputes(locked);
CREATE INDEX idx_dispute_resolved ON disputes(is_resolved);
CREATE INDEX idx_dispute_customer_favor ON disputes(resolved_in_customer_favor);

CREATE INDEX idx_issuer_code ON disputes(issuer_code);
CREATE INDEX idx_acquirer_code ON disputes(acquirer_code);
CREATE INDEX idx_disputant_domain_code ON disputes(disputant_domain_code);
