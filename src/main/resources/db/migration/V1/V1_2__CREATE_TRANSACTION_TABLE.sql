CREATE TABLE transactions (
    id BIGSERIAL PRIMARY KEY,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP,
    domain_code VARCHAR(20) NOT NULL,
    transaction_ref VARCHAR(100) NOT NULL,
    transaction_date TIMESTAMPTZ NOT NULL,
    amount NUMERIC(20, 2) NOT NULL,
    transaction_type_id BIGINT REFERENCES transaction_types(id),
    error_type VARCHAR(50),
    retrieval_reference_number VARCHAR(50),
    stan VARCHAR(20),
    transaction_currency_code VARCHAR(10),
    transaction_rail JSONB,
    additional_information JSONB,

    UNIQUE (domain_code, transaction_ref);
    CREATE INDEX idx_transactions_transaction_type_id ON transactions(transaction_type_id);
    CREATE INDEX idx_transactions_transaction_date ON transactions(transaction_date);

    CREATE INDEX idx_transactions_transaction_rail_gin ON transactions USING GIN (transaction_rail);
);