CREATE TABLE other_transaction_infos (
    /* ---------------------- BaseEntity ---------------------- */
    id BIGSERIAL PRIMARY KEY,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW(),

    /* ---------------------- Core Identifiers ---------------------- */
    rrn VARCHAR(20),                         -- Retrieval Reference Number
    stan VARCHAR(20),                        -- System Trace Audit Number
    authorization_code VARCHAR(20),
    currency VARCHAR(10),                    -- ISO currency code (NGN, USD, etc)

    /* ---------------------- Transaction Error ---------------------- */
    error_type VARCHAR(50),                  -- TransactionErrorType
    error_code VARCHAR(50),
    error_message TEXT,

    /* ---------------------- Transaction Rail ---------------------- */
    transaction_instrument VARCHAR(50),      -- TransactionInstrument
    payment_rail VARCHAR(50),                -- PaymentRail
    payment_gateway VARCHAR(50),              -- PaymentGateway
    facilitator VARCHAR(50),                 -- Facilitator

    /* ---------------------- Card Info (if applicable) ---------------------- */
    card_scheme VARCHAR(50),                 -- CardScheme
    card_first_six_digits CHAR(6),
    card_last_four_digits CHAR(4),
    card_digit_length INTEGER DEFAULT 16,

    /* ---------------------- Settlement ---------------------- */
    settlement_date TIMESTAMP,
    is_settled BOOLEAN,

    /* ---------------------- Posting ---------------------- */
    posting_date TIMESTAMP,
    is_posted BOOLEAN,

    /* ---------------------- Reversal ---------------------- */
    reversal_date TIMESTAMP,
    is_reversed BOOLEAN
);

-- Core identifiers
CREATE INDEX idx_oti_rrn ON other_transaction_infos(rrn);
CREATE INDEX idx_oti_stan ON other_transaction_infos(stan);
CREATE INDEX idx_oti_auth_code ON other_transaction_infos(authorization_code);

-- Error analysis
CREATE INDEX idx_oti_error_type ON other_transaction_infos(error_type);
CREATE INDEX idx_oti_error_code ON other_transaction_infos(error_code);

-- Rail & routing
CREATE INDEX idx_oti_payment_rail ON other_transaction_infos(payment_rail);
CREATE INDEX idx_oti_payment_gateway ON other_transaction_infos(payment_gateway);
CREATE INDEX idx_oti_instrument ON other_transaction_infos(transaction_instrument);

-- Card analytics
CREATE INDEX idx_oti_card_scheme ON other_transaction_infos(card_scheme);
CREATE INDEX idx_oti_card_bin ON other_transaction_infos(card_first_six_digits);

-- Lifecycle flags
CREATE INDEX idx_oti_is_settled ON other_transaction_infos(is_settled);
CREATE INDEX idx_oti_is_posted ON other_transaction_infos(is_posted);
CREATE INDEX idx_oti_is_reversed ON other_transaction_infos(is_reversed);

