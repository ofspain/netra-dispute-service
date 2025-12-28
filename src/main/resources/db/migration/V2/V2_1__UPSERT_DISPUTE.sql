CREATE OR REPLACE FUNCTION upsert_dispute(
    /* ---------------------- Identity ---------------------- */
    p_id BIGINT DEFAULT NULL,
    p_log_code VARCHAR(80) DEFAULT NULL,

    /* ---------------------- Transaction ---------------------- */
    p_transaction_info_id BIGINT DEFAULT NULL,
    p_transaction_date TIMESTAMP DEFAULT NULL,
    p_transaction_amount NUMERIC(18,2) DEFAULT NULL,
    p_transaction_action VARCHAR(50) DEFAULT NULL,
    p_transaction_payment_rail VARCHAR(50) DEFAULT NULL,
    p_transaction_instrument VARCHAR(50) DEFAULT NULL,

    /* ---------------------- State ---------------------- */
    p_dispute_marked_legit_time TIMESTAMP DEFAULT NULL,
    p_current_state VARCHAR(50) DEFAULT NULL,
    p_dispute_mode VARCHAR(50) DEFAULT NULL,
    p_locked BOOLEAN DEFAULT FALSE,

    /* ---------------------- Creation Context ---------------------- */
    p_created_via VARCHAR(50) DEFAULT NULL,

    /* ---------------------- Disputant ---------------------- */
    p_disputant_identity_uuid VARCHAR(100) DEFAULT NULL,
    p_disputant_type VARCHAR(50) DEFAULT NULL,
    p_disputant_domain_code VARCHAR(50) DEFAULT NULL,

    p_note TEXT DEFAULT NULL,

    /* ---------------------- Institution Codes ---------------------- */
    p_issuer_code VARCHAR(50) DEFAULT NULL,
    p_acquirer_code VARCHAR(50) DEFAULT NULL,
    p_merchant_code VARCHAR(50) DEFAULT NULL,
    p_beneficiary_code VARCHAR(50) DEFAULT NULL,
    p_switcher_code VARCHAR(50) DEFAULT NULL,
    p_biller_code VARCHAR(50) DEFAULT NULL,

    /* ---------------------- Legal Roles ---------------------- */
    p_plaintiff_institution_code VARCHAR(50) DEFAULT NULL,
    p_defendant_institution_code VARCHAR(50) DEFAULT NULL,

    /* ---------------------- Resolution Flags ---------------------- */
    p_is_finalized BOOLEAN DEFAULT FALSE,
    p_is_resolved BOOLEAN DEFAULT FALSE,
    p_resolved_in_customer_favor BOOLEAN DEFAULT FALSE
) RETURNS disputes AS $$
DECLARE
v_record disputes;
    v_new_id BIGINT;
    v_previous_state VARCHAR(50);
    v_on_us BOOLEAN;
BEGIN
    /* ---------------------- Derived Fields ---------------------- */
    v_on_us :=
        p_plaintiff_institution_code IS NOT NULL
        AND p_plaintiff_institution_code = p_defendant_institution_code;

    /* ---------------------- INSERT ---------------------- */
    IF p_id IS NULL THEN
        INSERT INTO disputes (
            transaction_info_id,
            transaction_date,
            transaction_amount,
            transaction_action,
            transaction_payment_rail,
            transaction_instrument,

            dispute_marked_legit_time,
            current_state,
            previous_state,
            dispute_mode,
            locked,

            created_via,
            disputant_identity_uuid,
            disputant_type,
            disputant_domain_code,
            note,

            issuer_code,
            acquirer_code,
            merchant_code,
            beneficiary_code,
            switcher_code,
            biller_code,

            plaintiff_institution_code,
            defendant_institution_code,
            on_us_transaction,

            is_finalized,
            is_resolved,
            resolved_in_customer_favor
        ) VALUES (
            p_transaction_info_id,
            p_transaction_date,
            p_transaction_amount,
            p_transaction_action,
            p_transaction_payment_rail,
            p_transaction_instrument,

            p_dispute_marked_legit_time,
            p_current_state,
            NULL,
            p_dispute_mode,
            p_locked,

            p_created_via,
            p_disputant_identity_uuid,
            p_disputant_type,
            p_disputant_domain_code,
            p_note,

            p_issuer_code,
            p_acquirer_code,
            p_merchant_code,
            p_beneficiary_code,
            p_switcher_code,
            p_biller_code,

            p_plaintiff_institution_code,
            p_defendant_institution_code,
            v_on_us,

            p_is_finalized,
            p_is_resolved,
            p_resolved_in_customer_favor
        )
        RETURNING id INTO v_new_id;

        /* Generate log_code AFTER insert */
        UPDATE disputes
            SET log_code = COALESCE(p_log_code, CONCAT(p_issuer_code, '-', v_new_id))
        WHERE id = v_new_id
        RETURNING * INTO v_record;

/* ---------------------- UPDATE ---------------------- */
    ELSE
        SELECT current_state
            INTO v_previous_state FROM disputes
        WHERE id = p_id
            FOR UPDATE;

        UPDATE disputes SET
            updated_at = NOW(),

            dispute_marked_legit_time = COALESCE(p_dispute_marked_legit_time, dispute_marked_legit_time),
            current_state = COALESCE(p_current_state, current_state),
            previous_state = v_previous_state,
            dispute_mode = COALESCE(p_dispute_mode, dispute_mode),
            locked = COALESCE(p_locked, locked),

            note = COALESCE(p_note, note),

            plaintiff_institution_code = COALESCE(p_plaintiff_institution_code, plaintiff_institution_code),
            defendant_institution_code = COALESCE(p_defendant_institution_code, defendant_institution_code),
            on_us_transaction = v_on_us,

            is_finalized = COALESCE(p_is_finalized, is_finalized),
            is_resolved = COALESCE(p_is_resolved, is_resolved),
            resolved_in_customer_favor = COALESCE(p_resolved_in_customer_favor, resolved_in_customer_favor)
        WHERE id = p_id
        RETURNING * INTO v_record;
    END IF;

    RETURN v_record;
END;
$$ LANGUAGE plpgsql;
