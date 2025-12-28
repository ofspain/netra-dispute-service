CREATE OR REPLACE FUNCTION upsert_other_transaction_info(
    /* ---------------------- Identity ---------------------- */
    p_id BIGINT DEFAULT NULL,
    p_rrn VARCHAR(20) DEFAULT NULL,
    p_stan VARCHAR(20) DEFAULT NULL,

    /* ---------------------- Core ---------------------- */
    p_authorization_code VARCHAR(20) DEFAULT NULL,
    p_currency VARCHAR(10) DEFAULT NULL,

    /* ---------------------- Error ---------------------- */
    p_error_type VARCHAR(50) DEFAULT NULL,
    p_error_code VARCHAR(50) DEFAULT NULL,
    p_error_message TEXT DEFAULT NULL,

    /* ---------------------- Rail ---------------------- */
    p_transaction_instrument VARCHAR(50) DEFAULT NULL,
    p_payment_rail VARCHAR(50) DEFAULT NULL,
    p_payment_gateway VARCHAR(50) DEFAULT NULL,
    p_facilitator VARCHAR(50) DEFAULT NULL,

    /* ---------------------- Card ---------------------- */
    p_card_scheme VARCHAR(50) DEFAULT NULL,
    p_card_first_six_digits CHAR(6) DEFAULT NULL,
    p_card_last_four_digits CHAR(4) DEFAULT NULL,
    p_card_digit_length INTEGER DEFAULT NULL,

    /* ---------------------- Settlement ---------------------- */
    p_settlement_date TIMESTAMP DEFAULT NULL,
    p_is_settled BOOLEAN DEFAULT NULL,

    /* ---------------------- Posting ---------------------- */
    p_posting_date TIMESTAMP DEFAULT NULL,
    p_is_posted BOOLEAN DEFAULT NULL,

    /* ---------------------- Reversal ---------------------- */
    p_reversal_date TIMESTAMP DEFAULT NULL,
    p_is_reversed BOOLEAN DEFAULT NULL
) RETURNS other_transaction_infos AS $$
DECLARE
v_record other_transaction_infos;
    v_existing_id BIGINT;
BEGIN
    /* ---------------------- Resolve Target Row ---------------------- */
    IF p_id IS NOT NULL THEN
        v_existing_id := p_id;
    ELSE
        SELECT id INTO v_existing_id FROM other_transaction_infos
            WHERE rrn = p_rrn AND stan = p_stan
        FOR UPDATE;
    END IF;

    /* ---------------------- INSERT ---------------------- */
    IF v_existing_id IS NULL THEN
        INSERT INTO other_transaction_infos (
            rrn,
            stan,
            authorization_code,
            currency,

            error_type,
            error_code,
            error_message,

            transaction_instrument,
            payment_rail,
            payment_gateway,
            facilitator,

            card_scheme,
            card_first_six_digits,
            card_last_four_digits,
            card_digit_length,

            settlement_date,
            is_settled,

            posting_date,
            is_posted,

            reversal_date,
            is_reversed
        ) VALUES (
            p_rrn,
            p_stan,
            p_authorization_code,
            p_currency,

            p_error_type,
            p_error_code,
            p_error_message,

            p_transaction_instrument,
            p_payment_rail,
            p_payment_gateway,
            p_facilitator,

            p_card_scheme,
            p_card_first_six_digits,
            p_card_last_four_digits,
            COALESCE(p_card_digit_length, 16),

            p_settlement_date,
            p_is_settled,

            p_posting_date,
            p_is_posted,

            p_reversal_date,
            p_is_reversed
        )
        RETURNING * INTO v_record;

    /* ---------------------- UPDATE ---------------------- */
    ELSE
        UPDATE other_transaction_infos SET
            updated_at = NOW(),
            authorization_code = COALESCE(p_authorization_code, authorization_code),
            currency = COALESCE(p_currency, currency),
            error_type = COALESCE(p_error_type, error_type),
            error_code = COALESCE(p_error_code, error_code),
            error_message = COALESCE(p_error_message, error_message),
            transaction_instrument = COALESCE(p_transaction_instrument, transaction_instrument),
            payment_rail = COALESCE(p_payment_rail, payment_rail),
            payment_gateway = COALESCE(p_payment_gateway, payment_gateway),
            facilitator = COALESCE(p_facilitator, facilitator),
            card_scheme = COALESCE(p_card_scheme, card_scheme),
            card_first_six_digits = COALESCE(p_card_first_six_digits, card_first_six_digits),
            card_last_four_digits = COALESCE(p_card_last_four_digits, card_last_four_digits),
            card_digit_length = COALESCE(p_card_digit_length, card_digit_length),
            settlement_date = COALESCE(p_settlement_date, settlement_date),
            is_settled = COALESCE(p_is_settled, is_settled),
            posting_date = COALESCE(p_posting_date, posting_date),
            is_posted = COALESCE(p_is_posted, is_posted),
            reversal_date = COALESCE(p_reversal_date, reversal_date),
            is_reversed = COALESCE(p_is_reversed, is_reversed)
        WHERE id = v_existing_id
        RETURNING * INTO v_record;
    END IF;

    RETURN v_record;
END;
$$ LANGUAGE plpgsql;
