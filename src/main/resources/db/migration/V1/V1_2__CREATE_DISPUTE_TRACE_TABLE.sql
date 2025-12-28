-- V1__create_dispute_journey_trace.sql

CREATE TABLE dispute_journey_traces (
    /* ---------------------- BaseEntity ---------------------- */
       id BIGSERIAL PRIMARY KEY,
       created_at TIMESTAMP NOT NULL DEFAULT NOW(),
       updated_at TIMESTAMP DEFAULT NOW(),

    /* ---------------------- Parent ---------------------- */
       dispute_id BIGINT NOT NULL REFERENCES disputes(id) ON DELETE CASCADE,

    /* ---------------------- State Transition ---------------------- */
       from_state VARCHAR(50),                -- DisputeState
       to_state VARCHAR(50),                  -- DisputeState
       transition_time TIMESTAMP NOT NULL,

    /* ---------------------- Initiator ---------------------- */
       initiated_by VARCHAR(50),              -- ISSUER / ACQUIRER / SYSTEM
       initiated_by_domain_code VARCHAR(50),  -- institution / domain code
       initiated_by_uuid VARCHAR(100),        -- user or actor UUID
       initiated_by_domain_type VARCHAR(50),  -- CUSTOMERUSER / INSTITUTIONUSER / INTERNALUSER

    /* ---------------------- Context ---------------------- */
       reason TEXT,
       application_channel VARCHAR(50)        -- API / PORTAL / SYSTEM
);
