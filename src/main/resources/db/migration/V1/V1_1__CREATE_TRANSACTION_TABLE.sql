CREATE TABLE transactions (
    --start base entity
    id BIGSERIAL PRIMARY KEY,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP,
    --end base entity

    --domains/entities
    issuer JSONB,
    acquirer JSONB,
    beneficiary JSONB,
    access_point JSONB,

    --intrinsic transaction properties
    transaction_ref VARCHAR(100) NOT NULL,
    transaction_date TIMESTAMPTZ NOT NULL,
    amount NUMERIC(20, 2) NOT NULL,
    transaction_type_id BIGINT REFERENCES transaction_types(id),
    transaction_rail JSONB,
    rrn VARCHAR(50),
    stan VARCHAR(30),
    currency_id BIGINT REFERENCES currencies(id),
    -- error related
    error_type VARCHAR(70),
    error_code VARCHAR(70),
    error_message text,

    card JSONB,
    authorization_code VARCHAR(50),
    disputability_check JSONB,

    additional_information JSONB

 );

CREATE INDEX idx_transactions_transaction_type_id ON transactions(transaction_type_id);
CREATE INDEX idx_transactions_transaction_date ON transactions(transaction_date);
CREATE INDEX idx_transactions_error ON transactions(error_type);

CREATE INDEX idx_transactions_transaction_rail_gin ON transactions USING GIN (transaction_rail);
