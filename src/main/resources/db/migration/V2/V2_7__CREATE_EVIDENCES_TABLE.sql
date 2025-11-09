-- ================================================
-- Table: evidences
-- ================================================

CREATE TABLE evidences (
    id BIGSERIAL PRIMARY KEY,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP,

    evidence_type VARCHAR(50) NOT NULL,            -- Enum: EvidenceType
    dispute_id BIGINT,                             -- FK to disputes (if exists)

    uuid VARCHAR(100) NOT NULL,                    -- Unique identifier
    evidence_key VARCHAR(255) NOT NULL,                  -- Full S3 path
    original_filename VARCHAR(255),
    extension VARCHAR(20),
    content_type VARCHAR(100),
    remarks TEXT,

    average_hash VARCHAR(128),                     -- For duplication detection
    perpetual_hash VARCHAR(128),                   -- For duplication detection
    uploaded_by_actor VARCHAR(50),                 -- Enum: EvidenceActor
    evidence_context VARCHAR(50),                  -- Enum: EvidenceContext
    uploaded_by VARCHAR(100),                      -- Identifier of uploader

    CONSTRAINT uq_evidence_uuid UNIQUE (uuid),
    CONSTRAINT uq_evidence_uuid UNIQUE (evidence_key),
    CONSTRAINT fk_evidence_dispute FOREIGN KEY (dispute_id) REFERENCES disputes(id)
);

-- ================================================
-- Indexes
-- ================================================

-- Speed up searches by dispute_id (if querying evidence by dispute)
CREATE INDEX idx_evidences_dispute_id ON evidences(dispute_id);

-- Speed up duplication checks (first 16 chars of hash)
CREATE INDEX idx_evidences_average_hash_prefix ON evidences ((LEFT(average_hash, 16)));
CREATE INDEX idx_evidences_perpetual_hash_prefix ON evidences ((LEFT(perpetual_hash, 16)));

-- For filtering by type or context
CREATE INDEX idx_evidences_type ON evidences(evidence_type);
CREATE INDEX idx_evidences_context ON evidences(evidence_context);


