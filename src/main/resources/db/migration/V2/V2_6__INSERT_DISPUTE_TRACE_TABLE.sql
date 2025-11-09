-- 4️⃣ Recreate the immutable insert function (returns inserted row)
CREATE OR REPLACE FUNCTION insert_dispute_journey_trace(
    _dispute_id BIGINT,
    _from_state TEXT,
    _to_state TEXT,
    _transition_time TIMESTAMPTZ,
    _initiated_by TEXT,
    _initiated_by_code TEXT,
    _initiated_by_id BIGINT,
    _reason TEXT,
    _application_channel TEXT,
    _current_hash TEXT,
    _previous_hash TEXT,
    _digital_signature TEXT,
    _aptos_txn_hash TEXT,
    _aptos_event_ref TEXT
)
RETURNS dispute_journey_traces
LANGUAGE plpgsql
AS $$
DECLARE
_result dispute_journey_traces;
BEGIN
    -- Prevent duplicates (immutability)
    IF EXISTS (
        SELECT 1
        FROM dispute_journey_traces
        WHERE dispute_id = _dispute_id
          AND current_hash = _current_hash
    ) THEN
        RAISE EXCEPTION 'Record for dispute % with hash % already exists',
            _dispute_id, _current_hash;
END IF;

INSERT INTO dispute_journey_traces (
    dispute_id,
    from_state,
    to_state,
    transition_time,
    initiated_by,
    initiated_by_code,
    initiated_by_id,
    reason,
    application_channel,
    current_hash,
    previous_hash,
    digital_signature,
    aptos_txn_hash,
    aptos_event_ref
)
VALUES (
           _dispute_id,
           _from_state,
           _to_state,
           _transition_time,
           _initiated_by,
           _initiated_by_code,
           _initiated_by_id,
           _reason,
           _application_channel,
           _current_hash,
           _previous_hash,
           _digital_signature,
           _aptos_txn_hash,
           _aptos_event_ref
       )
    RETURNING * INTO _result;

RETURN _result;
END;
$$;

-- 5️⃣ Recreate immutability enforcement (no UPDATE/DELETE allowed)
CREATE OR REPLACE FUNCTION prevent_modifications()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'dispute_journey_traces table is immutable';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER prevent_update_or_delete
    BEFORE UPDATE OR DELETE ON dispute_journey_traces
    FOR EACH ROW
EXECUTE FUNCTION prevent_modifications();
