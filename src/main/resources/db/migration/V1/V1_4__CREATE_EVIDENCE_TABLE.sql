CREATE TABLE evidences (
    /* ---------------------- BaseEntity ---------------------- */
    id BIGSERIAL PRIMARY KEY,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW(),

    /* ---------------------- Association ---------------------- */
    dispute_id BIGINT NOT NULL REFERENCES disputes(id) ON DELETE CASCADE,

    /* ---------------------- Evidence Identity ---------------------- */
    evidence_type VARCHAR(50) NOT NULL,       -- EvidenceType enum

    uuid UUID NOT NULL UNIQUE,                 -- used in storage key
    evidence_key TEXT NOT NULL,                -- full path (e.g. s3://bucket/evidence/uuid.jpg)

    original_filename TEXT NOT NULL,
    extension VARCHAR(10),                     -- jpg, png, pdf
    content_type VARCHAR(100),                 -- image/jpeg, application/pdf

    remarks TEXT,                              -- free-text note

    /* ---------------------- Duplication Detection ---------------------- */
    average_hash CHAR(64),                     -- perceptual / avg hash
    perpetual_hash CHAR(64),                   -- strong hash (sha256, etc)

    /* ---------------------- Actor & Context ---------------------- */
    uploaded_by_actor VARCHAR(50),             -- EvidenceActor enum
    evidence_context VARCHAR(50),              -- EvidenceContext enum
    uploaded_by VARCHAR(100)                   -- uploader identifier (uuid / username)
);

-- Parent lookup
CREATE INDEX idx_evidence_dispute_id
    ON evidences(dispute_id);

-- Evidence classification
CREATE INDEX idx_evidence_type
    ON evidences(evidence_type);

CREATE INDEX idx_evidence_context
    ON evidences(evidence_context);

-- Storage & identity
CREATE INDEX idx_evidence_key
    ON evidences(evidence_key);

CREATE INDEX idx_evidence_uuid
    ON evidences(uuid);

-- Time-based queries
CREATE INDEX idx_evidence_created_at
    ON evidences(created_at);

-- Duplicate detection (prefix-based searches)
CREATE INDEX idx_evidence_avg_hash_prefix
    ON evidences (SUBSTRING(average_hash FROM 1 FOR 16));

CREATE INDEX idx_evidence_perpetual_hash_prefix
    ON evidences (SUBSTRING(perpetual_hash FROM 1 FOR 16));
