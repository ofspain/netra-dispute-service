CREATE OR REPLACE FUNCTION insert_dispute_journey_trace(
    /* ---------------------- Parent ---------------------- */
    p_dispute_id BIGINT,

    /* ---------------------- State Transition ---------------------- */
    p_from_state VARCHAR(50),
    p_to_state VARCHAR(50),
    p_transition_time TIMESTAMP DEFAULT NOW(),

    /* ---------------------- Initiator ---------------------- */
    p_initiated_by VARCHAR(50),
    p_initiated_by_domain_code VARCHAR(50),
    p_initiated_by_uuid VARCHAR(100),
    p_initiated_by_domain_type VARCHAR(50),

    /* ---------------------- Context ---------------------- */
    p_reason TEXT DEFAULT NULL,
    p_application_channel VARCHAR(50) DEFAULT NULL
) RETURNS dispute_journey_traces AS $$
DECLARE
v_record dispute_journey_traces;
BEGIN
INSERT INTO dispute_journey_traces (
    dispute_id,
    from_state,
    to_state,
    transition_time,
    initiated_by,
    initiated_by_domain_code,
    initiated_by_uuid,
    initiated_by_domain_type,
    reason,
    application_channel
) VALUES (
             p_dispute_id,
             p_from_state,
             p_to_state,
             COALESCE(p_transition_time, NOW()),
             p_initiated_by,
             p_initiated_by_domain_code,
             p_initiated_by_uuid,
             p_initiated_by_domain_type,
             p_reason,
             p_application_channel
         )
    RETURNING * INTO v_record;

RETURN v_record;
END;
$$ LANGUAGE plpgsql;
