CREATE OR REPLACE FUNCTION get_full_dispute(
    p_dispute_id BIGINT DEFAULT NULL,
    p_dispute_log_code VARCHAR DEFAULT NULL,
    p_evidence_limit INT DEFAULT 100,
    p_evidence_offset INT DEFAULT 0,
    p_journey_limit INT DEFAULT 50,
    p_journey_offset INT DEFAULT 0
)
RETURNS TABLE(
    id BIGINT,
    log_code VARCHAR,
    current_state VARCHAR,
    -- add other dispute columns as needed
    evidences JSON,
    journey_traces JSON
) AS $$
BEGIN
RETURN QUERY
    WITH dispute_info AS (
        SELECT d.*, oti.*
        FROM disputes d
        LEFT JOIN other_transaction_infos oti
               ON oti.id = d.transaction_info_id
        WHERE (p_dispute_id IS NOT NULL AND d.id = p_dispute_id)
           OR (p_dispute_log_code IS NOT NULL AND d.log_code = p_dispute_log_code)
    ),
    evidence_paged AS (
        SELECT *
        FROM evidences
        WHERE dispute_id IN (SELECT id FROM dispute_info)
        ORDER BY created_at ASC
        LIMIT COALESCE(p_evidence_limit, 100)
        OFFSET COALESCE(p_evidence_offset, 0)
    ),
    evidence_info AS (
        SELECT
            e.dispute_id,
            JSON_AGG(
                JSON_BUILD_OBJECT(
                    'id', e.id,
                    'uuid', e.uuid,
                    'evidence_key', e.evidence_key,
                    'evidence_type', e.evidence_type,
                    'original_filename', e.original_filename,
                    'extension', e.extension,
                    'content_type', e.content_type,
                    'remarks', e.remarks,
                    'average_hash', e.average_hash,
                    'perpetual_hash', e.perpetual_hash,
                    'uploaded_by_actor', e.uploaded_by_actor,
                    'evidence_context', e.evidence_context,
                    'uploaded_by', e.uploaded_by,
                    'created_at', e.created_at
                ) ORDER BY e.created_at ASC
            ) AS evidences
        FROM evidence_paged e
        GROUP BY e.dispute_id
    ),
    journey_paged AS (
        SELECT *
        FROM dispute_journey_traces
        WHERE dispute_id IN (SELECT id FROM dispute_info)
        ORDER BY transition_time ASC
        LIMIT COALESCE(p_journey_limit, 50)
        OFFSET COALESCE(p_journey_offset, 0)
    ),
    journey_info AS (
        SELECT
            jt.dispute_id,
            JSON_AGG(
                JSON_BUILD_OBJECT(
                    'id', jt.id,
                    'from_state', jt.from_state,
                    'to_state', jt.to_state,
                    'transition_time', jt.transition_time,
                    'initiated_by', jt.initiated_by,
                    'initiated_by_domain_code', jt.initiated_by_domain_code,
                    'initiated_by_uuid', jt.initiated_by_uuid,
                    'initiated_by_domain_type', jt.initiated_by_domain_type,
                    'reason', jt.reason,
                    'application_channel', jt.application_channel
                ) ORDER BY jt.transition_time ASC
            ) AS journey_traces
        FROM journey_paged jt
        GROUP BY jt.dispute_id
    )
SELECT
    di.id,
    di.log_code,
    di.transaction_date,
    di.transaction_amount,
    di.transaction_action,
    di.transaction_payment_rail,
    di.transaction_instrument,
    di.current_state,
    di.previous_state,
    di.dispute_mode,
    di.locked,
    di.created_via,
    di.disputant_identity_uuid,
    di.disputant_type,
    di.disputant_domain_code,
    di.note,
    di.issuer_code,
    di.acquirer_code,
    di.merchant_code,
    di.beneficiary_code,
    di.switcher_code,
    di.biller_code,
    di.plaintiff_institution_code,
    di.defendant_institution_code,
    di.on_us_transaction,
    di.is_finalized,
    di.is_resolved,
    di.resolved_in_customer_favor,

    di.rrn,
    di.stan,
    di.authorization_code,
    di.currency,
    di.transaction_instrument AS oti_instrument,
    di.payment_rail AS oti_rail,
    di.payment_gateway AS oti_gateway,
    di.facilitator AS oti_facilitator,
    di.settlement_date,
    di.is_settled,
    di.posting_date,
    di.is_posted,
    di.reversal_date,
    di.is_reversed,

    COALESCE(ei.evidences, '[]'::json) AS evidences,
    COALESCE(ji.journey_traces, '[]'::json) AS journey_traces
FROM dispute_info di
         LEFT JOIN evidence_info ei ON ei.dispute_id = di.id
         LEFT JOIN journey_info ji ON ji.dispute_id = di.id;

END;
$$ LANGUAGE plpgsql;
