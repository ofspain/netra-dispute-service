-- V1__create_dispute_journey_trace.sql

-- 1. Create the table
CREATE TABLE dispute_journey_traces (
    id BIGSERIAL PRIMARY KEY,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW(),
    dispute_id BIGINT NOT NULL REFERENCES dispute(id) ON DELETE CASCADE,

    from_state VARCHAR(50),
    to_state VARCHAR(50),
    transition_time TIMESTAMP NOT NULL,
    initiated_by VARCHAR(50),
    initiated_by_code VARCHAR(50),
    initiated_by_id BIGINT,
    reason TEXT,
    application_channel VARCHAR(50),

    current_hash TEXT,
    previous_hash TEXT,
    audit_trace TEXT,
    digital_signature TEXT,

    UNIQUE (current_hash)
);

-- 2. Create the immutability enforcement function
CREATE OR REPLACE FUNCTION prevent_modifications()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'DisputeJourneyTrace is immutable';
END;
$$ LANGUAGE plpgsql;

-- 3. Attach the trigger to the table
CREATE TRIGGER prevent_update_or_delete
    BEFORE UPDATE OR DELETE ON dispute_journey_trace
FOR EACH ROW EXECUTE FUNCTION prevent_modifications();
