CREATE TABLE disputes (
    /* ---------------------- BaseEntity ---------------------- */
                          id BIGSERIAL PRIMARY KEY,
                          created_at TIMESTAMP NOT NULL DEFAULT NOW(),
                          updated_at TIMESTAMP DEFAULT NOW(),

    /* ---------------------- Core Identity ---------------------- */
                          log_code VARCHAR(80) UNIQUE, -- generated at DB or service layer

    /* ---------------------- Transaction Link ---------------------- */
                          transaction_info_id BIGINT REFERENCES transactions(id) ON DELETE CASCADE,

    /* Snapshot of transaction details */
                          transaction_date TIMESTAMP,
                          transaction_amount NUMERIC(18, 2),

                          transaction_action VARCHAR(50),          -- TransactionAction enum
                          transaction_payment_rail VARCHAR(50),    -- PaymentRail enum
                          transaction_instrument VARCHAR(50),      -- TransactionInstrument enum

    /* ---------------------- Dispute State ---------------------- */
                          dispute_marked_legit_time TIMESTAMP,      -- issuer verified time
                          current_state VARCHAR(50) NOT NULL,       -- DisputeState
                          previous_state VARCHAR(50),

                          dispute_mode VARCHAR(50),                 -- DisputeMode enum
                          locked BOOLEAN NOT NULL DEFAULT FALSE,

    /* ---------------------- Creation Context ---------------------- */
                          created_via VARCHAR(50) NOT NULL,          -- ApplicationChannel enum

    /* Disputant (createdBy) */
                          disputant_identity_uuid VARCHAR(100) NOT NULL,
                          disputant_type VARCHAR(50) NOT NULL,
                          disputant_domain_code VARCHAR(50) NOT NULL,

                          note TEXT,

    /* ---------------------- Institution Codes ---------------------- */
                          issuer_code VARCHAR(50),
                          acquirer_code VARCHAR(50),
                          merchant_code VARCHAR(50),
                          beneficiary_code VARCHAR(50),
                          switcher_code VARCHAR(50),
                          biller_code VARCHAR(50),

    /* ---------------------- Legal / Institutional Roles ---------------------- */
                          plaintiff_institution_code VARCHAR(50),
                          defendant_institution_code VARCHAR(50),

                          on_us_transaction BOOLEAN NOT NULL DEFAULT FALSE,

    /* ---------------------- Resolution Flags ---------------------- */
                          is_finalized BOOLEAN NOT NULL DEFAULT FALSE,
                          is_resolved BOOLEAN NOT NULL DEFAULT FALSE,
                          resolved_in_customer_favor BOOLEAN NOT NULL DEFAULT FALSE
);


-- Time-based queries
CREATE INDEX idx_dispute_created_at ON disputes(created_at);
CREATE INDEX idx_dispute_updated_at ON disputes(updated_at);
CREATE INDEX idx_dispute_legit_time ON disputes(dispute_marked_legit_time);

-- Identity & joins
CREATE INDEX idx_dispute_transaction_info_id ON disputes(transaction_info_id);
CREATE INDEX idx_dispute_log_code ON disputes(log_code);

-- State & workflow
CREATE INDEX idx_dispute_current_state ON disputes(current_state);
CREATE INDEX idx_dispute_dispute_mode ON disputes(dispute_mode);
CREATE INDEX idx_dispute_locked ON disputes(locked);

-- Resolution flags
CREATE INDEX idx_dispute_finalized ON disputes(is_finalized);
CREATE INDEX idx_dispute_resolved ON disputes(is_resolved);
CREATE INDEX idx_dispute_customer_favor ON disputes(resolved_in_customer_favor);

-- Institutional filtering
CREATE INDEX idx_issuer_code ON disputes(issuer_code);
CREATE INDEX idx_acquirer_code ON disputes(acquirer_code);
CREATE INDEX idx_plaintiff_institution_code ON disputes(plaintiff_institution_code);
CREATE INDEX idx_defendant_institution_code ON disputes(defendant_institution_code);
CREATE INDEX idx_on_us_transaction ON disputes(on_us_transaction);

-- Disputant
CREATE INDEX idx_disputant_domain_code ON disputes(disputant_domain_code);
CREATE INDEX idx_disputant_type ON disputes(disputant_type);

