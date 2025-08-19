CREATE OR REPLACE FUNCTION upsert_dispute(
    p_id BIGINT DEFAULT NULL,
    p_transaction_id BIGINT DEFAULT NULL,
    p_dispute_marked_legit_time TIMESTAMP DEFAULT NULL,
    p_current_state VARCHAR(50) DEFAULT NULL,
    p_created_via VARCHAR(50) DEFAULT NULL,
    p_disputant_identity_uuid BIGINT DEFAULT NULL,
    p_disputant_type VARCHAR(50) DEFAULT NULL,
    p_disputant_domain_code VARCHAR(50) DEFAULT NULL,
    p_note TEXT DEFAULT NULL,
    p_issuer_code VARCHAR(50) DEFAULT NULL,
    p_acquirer_code VARCHAR(50) DEFAULT NULL,
    p_merchant_code VARCHAR(50) DEFAULT NULL,
    p_beneficiary_code VARCHAR(50) DEFAULT NULL,
    p_switcher_code VARCHAR(50) DEFAULT NULL,
    p_locked BOOLEAN DEFAULT FALSE,
    p_is_finalized BOOLEAN DEFAULT FALSE,
    p_is_resolved BOOLEAN DEFAULT FALSE,
    p_resolved_in_customer_favor BOOLEAN DEFAULT FALSE
) RETURNS disputes AS $$
DECLARE
v_record disputes;
    v_new_id BIGINT;
    v_previous_state VARCHAR(50);
BEGIN
    -- Handle insert vs update
    IF p_id IS NULL THEN
        -- Insert new record - previous_state is always NULL for new disputes
        INSERT INTO disputes (
            transaction_id,
            dispute_marked_legit_time,
            current_state,
            previous_state, -- Explicitly NULL for new records
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
            locked,
            is_finalized,
            is_resolved,
            resolved_in_customer_favor
        ) VALUES (
            p_transaction_id,
            p_dispute_marked_legit_time,
            p_current_state,
            NULL, -- previous_state always NULL for new disputes
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
            p_locked,
            p_is_finalized,
            p_is_resolved,
            p_resolved_in_customer_favor
        ) RETURNING id INTO v_new_id;
        
        -- Generate log_code by appending issuer_code to the new ID
UPDATE disputes
SET log_code = CONCAT(p_issuer_code, '-', v_new_id)
WHERE id = v_new_id
    RETURNING * INTO v_record;
ELSE
        -- For updates, get the current state before updating
SELECT current_state INTO v_previous_state
FROM disputes
WHERE id = p_id;

-- Update existing record with proper state transition
UPDATE disputes SET
                    updated_at = NOW(),
                    dispute_marked_legit_time = p_dispute_marked_legit_time,
                    current_state = p_current_state,
                    previous_state = v_previous_state, -- Set to pre-update current_state
                    note = p_note,
                    locked = p_locked,
                    is_finalized = p_is_finalized,
                    is_resolved = p_is_resolved,
                    resolved_in_customer_favor = p_resolved_in_customer_favor
WHERE id = p_id
    RETURNING * INTO v_record;
END IF;

RETURN v_record;
END;
$$ LANGUAGE plpgsql;