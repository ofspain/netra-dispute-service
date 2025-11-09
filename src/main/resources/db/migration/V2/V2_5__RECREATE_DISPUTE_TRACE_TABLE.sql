-- ==============================================
-- Purpose: Drop the old immutable table and recreate the new blockchain-auditable one
-- ==============================================

-- 1️⃣ Drop dependent trigger and function if they exist
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_trigger WHERE tgname = 'prevent_update_or_delete') THEN
DROP TRIGGER prevent_update_or_delete ON dispute_journey_traces;
END IF;

    IF EXISTS (SELECT 1 FROM pg_proc WHERE proname = 'prevent_modifications') THEN
DROP FUNCTION prevent_modifications();
END IF;

    IF EXISTS (SELECT 1 FROM pg_proc WHERE proname = 'insert_dispute_journey_trace') THEN
DROP FUNCTION insert_dispute_journey_trace(
    TEXT, TEXT, TEXT, TIMESTAMPTZ,
    TEXT, TEXT, BIGINT,
    TEXT, TEXT,
    TEXT, TEXT, JSONB,
    TEXT, TEXT, TEXT
    );
END IF;
END $$;

-- 2️⃣ Drop the old table if it exists
DROP TABLE IF EXISTS dispute_journey_traces CASCADE;

-- 3️⃣ Recreate the table
CREATE TABLE dispute_journey_traces (
                                        id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                        created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

                                        dispute_id BIGINT NOT NULL REFERENCES disputes(id) ON DELETE CASCADE,
                                        from_state TEXT NOT NULL,
                                        to_state TEXT NOT NULL,
                                        transition_time TIMESTAMPTZ NOT NULL,

                                        initiated_by TEXT,
                                        initiated_by_code TEXT,
                                        initiated_by_id BIGINT,

                                        reason TEXT,
                                        application_channel TEXT,

                                        current_hash TEXT NOT NULL,
                                        previous_hash TEXT,
                                        digital_signature TEXT,
                                        aptos_txn_hash TEXT,
                                        aptos_event_ref TEXT,

                                        UNIQUE (dispute_id, current_hash)
);