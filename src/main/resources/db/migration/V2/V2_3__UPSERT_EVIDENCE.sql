CREATE OR REPLACE FUNCTION upsert_evidence(
    /* ---------------------- Identity ---------------------- */
    p_id BIGINT DEFAULT NULL,
    p_dispute_id BIGINT DEFAULT NULL,
    p_uuid UUID DEFAULT NULL,

    /* ---------------------- Evidence ---------------------- */
    p_evidence_type VARCHAR(50) DEFAULT NULL,
    p_evidence_key TEXT DEFAULT NULL,
    p_original_filename TEXT DEFAULT NULL,
    p_extension VARCHAR(10) DEFAULT NULL,
    p_content_type VARCHAR(100) DEFAULT NULL,
    p_remarks TEXT DEFAULT NULL,

    /* ---------------------- Duplication ---------------------- */
    p_average_hash CHAR(64) DEFAULT NULL,
    p_perpetual_hash CHAR(64) DEFAULT NULL,

    /* ---------------------- Actor & Context ---------------------- */
    p_uploaded_by_actor VARCHAR(50) DEFAULT NULL,
    p_evidence_context VARCHAR(50) DEFAULT NULL,
    p_uploaded_by VARCHAR(100) DEFAULT NULL
) RETURNS evidences AS $$
DECLARE
v_record evidences;
    v_existing_id BIGINT;
BEGIN
    /* ---------------------- Resolve Target Row ---------------------- */
    IF p_id IS NOT NULL THEN
        v_existing_id := p_id;
    ELSE
        SELECT id INTO v_existing_id FROM evidences
            WHERE uuid = p_uuid
        FOR UPDATE;
    END IF;

    /* ---------------------- INSERT ---------------------- */
    IF v_existing_id IS NULL THEN
        INSERT INTO evidences (
            dispute_id,
            evidence_type,
            uuid,
            evidence_key,
            original_filename,
            extension,
            content_type,
            remarks,
            average_hash,
            perpetual_hash,
            uploaded_by_actor,
            evidence_context,
            uploaded_by
        ) VALUES (
            p_dispute_id,
            p_evidence_type,
            p_uuid,
            p_evidence_key,
            p_original_filename,
            p_extension,
            p_content_type,
            p_remarks,
            p_average_hash,
            p_perpetual_hash,
            p_uploaded_by_actor,
            p_evidence_context,
            p_uploaded_by
        )
        RETURNING * INTO v_record;

    /* ---------------------- UPDATE (PATCH STYLE) ---------------------- */
    ELSE
        UPDATE evidences SET
          updated_at = NOW(),
          dispute_id = COALESCE(p_dispute_id, dispute_id),
          evidence_type = COALESCE(p_evidence_type, evidence_type),
          evidence_key = COALESCE(p_evidence_key, evidence_key),
          original_filename = COALESCE(p_original_filename, original_filename),
          extension = COALESCE(p_extension, extension),
          content_type = COALESCE(p_content_type, content_type),
          remarks = COALESCE(p_remarks, remarks),
          average_hash = COALESCE(p_average_hash, average_hash),
          perpetual_hash = COALESCE(p_perpetual_hash, perpetual_hash),
          uploaded_by_actor = COALESCE(p_uploaded_by_actor, uploaded_by_actor),
          evidence_context = COALESCE(p_evidence_context, evidence_context),
          uploaded_by = COALESCE(p_uploaded_by, uploaded_by)
        WHERE id = v_existing_id
        RETURNING * INTO v_record;
    END IF;

    RETURN v_record;
END;
$$ LANGUAGE plpgsql;
