CREATE TABLE evidences (
   id BIGSERIAL PRIMARY KEY,
   created_at TIMESTAMP NOT NULL DEFAULT NOW(),
   updated_at TIMESTAMP DEFAULT NOW(),

   evidence_type VARCHAR(50) NOT NULL,
   dispute_id BIGINT NOT NULL REFERENCES dispute(id) ON DELETE CASCADE,

   uuid UUID NOT NULL UNIQUE,
   s3_key TEXT NOT NULL,
   original_filename TEXT NOT NULL,
   extension VARCHAR(10),
   content_type VARCHAR(100),
   size BIGINT
);

-- Indexes
CREATE INDEX idx_evidence_dispute_id ON evidences(dispute_id);
CREATE INDEX idx_evidence_type ON evidences(evidence_type);
CREATE INDEX idx_evidence_key ON evidences(s3_key);
CREATE INDEX idx_evidence_uuid ON evidences(uuid);
CREATE INDEX idx_evidence_created_at ON evidences(created_at);
