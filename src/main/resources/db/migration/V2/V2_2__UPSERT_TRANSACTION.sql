CREATE OR REPLACE FUNCTION upsert_transaction(
    -- Base entity fields
    p_id BIGINT DEFAULT NULL,
    p_created_at TIMESTAMP DEFAULT NULL,

    -- Domain/entity fields
    p_issuer JSONB DEFAULT NULL,
    p_acquirer JSONB DEFAULT NULL,
    p_beneficiary JSONB DEFAULT NULL,
    p_access_point JSONB DEFAULT NULL,

    -- Transaction properties
    p_transaction_ref VARCHAR(100) DEFAULT NULL,
    p_transaction_date TIMESTAMPTZ DEFAULT NULL,
    p_amount NUMERIC(20, 2) DEFAULT NULL,
    p_transaction_type_id BIGINT DEFAULT NULL,
    p_transaction_rail JSONB DEFAULT NULL,
    p_rrn VARCHAR(50) DEFAULT NULL,
    p_stan VARCHAR(30) DEFAULT NULL,
    p_currency_id BIGINT DEFAULT NULL,

    -- Error fields
    p_error_type VARCHAR(70) DEFAULT NULL,
    p_error_code VARCHAR(70) DEFAULT NULL,
    p_error_message TEXT DEFAULT NULL,

    -- Card fields
    p_card JSONB DEFAULT NULL,
    p_authorization_code VARCHAR(50) DEFAULT NULL,

    -- Dispute fields
    p_disputability_check JSONB DEFAULT NULL,

    -- Additional info
    p_additional_information JSONB DEFAULT NULL
)
RETURNS BIGINT
LANGUAGE plpgsql
AS $$
DECLARE
v_id BIGINT;
BEGIN
    -- Validate required fields
    IF p_transaction_ref IS NULL OR p_transaction_date IS NULL OR
       p_amount IS NULL OR p_transaction_type_id IS NULL OR
       p_currency_id IS NULL THEN
        RAISE EXCEPTION 'Required fields: transaction_ref, transaction_date, amount, transaction_type_id, currency_id';
END IF;

    -- Check foreign key references
    PERFORM 1 FROM transaction_types WHERE id = p_transaction_type_id;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'Transaction type not found with ID %', p_transaction_type_id;
END IF;

    PERFORM 1 FROM currencies WHERE id = p_currency_id;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'Currency not found with ID %', p_currency_id;
END IF;

    -- Check for duplicate transaction_ref
    IF p_id IS NULL THEN
        PERFORM 1 FROM transactions WHERE transaction_ref = p_transaction_ref;
        IF FOUND THEN
            RAISE EXCEPTION 'Transaction with reference % already exists', p_transaction_ref;
END IF;
ELSE
        PERFORM 1 FROM transactions
        WHERE transaction_ref = p_transaction_ref AND id != p_id;
        IF FOUND THEN
            RAISE EXCEPTION 'Another transaction with reference % already exists', p_transaction_ref;
END IF;
END IF;

    -- Upsert the transaction
    IF p_id IS NULL THEN
        INSERT INTO transactions (
            created_at,
            updated_at,
            issuer,
            acquirer,
            beneficiary,
            access_point,
            transaction_ref,
            transaction_date,
            amount,
            transaction_type_id,
            transaction_rail,
            rrn,
            stan,
            currency_id,
            error_type,
            error_code,
            error_message,
            card,
            authorization_code,
            disputability_check,
            additional_information
        ) VALUES (
            COALESCE(p_created_at, NOW()),
            NOW(),
            p_issuer,
            p_acquirer,
            p_beneficiary,
            p_access_point,
            p_transaction_ref,
            p_transaction_date,
            p_amount,
            p_transaction_type_id,
            p_transaction_rail,
            p_rrn,
            p_stan,
            p_currency_id,
            p_error_type,
            p_error_code,
            p_error_message,
            p_card,
            p_authorization_code,
            p_disputability_check,
            p_additional_information
        )
        RETURNING id INTO v_id;
ELSE
UPDATE transactions
SET
    updated_at = NOW(),
    issuer = p_issuer,
    acquirer = p_acquirer,
    beneficiary = p_beneficiary,
    access_point = p_access_point,
    transaction_ref = p_transaction_ref,
    transaction_date = p_transaction_date,
    amount = p_amount,
    transaction_type_id = p_transaction_type_id,
    transaction_rail = p_transaction_rail,
    rrn = p_rrn,
    stan = p_stan,
    currency_id = p_currency_id,
    error_type = p_error_type,
    error_code = p_error_code,
    error_message = p_error_message,
    card = p_card,
    authorization_code = p_authorization_code,
    disputability_check = p_disputability_check,
    additional_information = p_additional_information
WHERE id = p_id
    RETURNING id INTO v_id;

IF NOT FOUND THEN
            RAISE EXCEPTION 'Transaction not found with ID %', p_id;
END IF;
END IF;

RETURN v_id;
END;
$$;