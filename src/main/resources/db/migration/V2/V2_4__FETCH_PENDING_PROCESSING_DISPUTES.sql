CREATE OR REPLACE FUNCTION get_pending_processing_disputes(
    p_limit INTEGER DEFAULT 50,
    p_offset INTEGER DEFAULT 0
)
RETURNS JSONB
LANGUAGE plpgsql
AS $$
DECLARE
result JSONB;
BEGIN
SELECT jsonb_build_object(
               'data', COALESCE(
                (SELECT jsonb_agg(
                                jsonb_build_object(
                                        'id', d.id,
                                        'created_at', d.created_at,
                                        'updated_at', d.updated_at,
                                        'transaction_id', d.transaction_id,
                                        'dispute_marked_legit_time', d.dispute_marked_legit_time,
                                        'current_state', d.current_state,
                                        'previous_state', d.previous_state,
                                        'created_via', d.created_via,
                                        'log_code', d.log_code,
                                        'disputant_identity_uuid', d.disputant_identity_uuid,
                                        'disputant_type', d.disputant_type,
                                        'disputant_domain_code', d.disputant_domain_code,
                                        'note', d.note,
                                        'issuer_code', d.issuer_code,
                                        'acquirer_code', d.acquirer_code,
                                        'merchant_code', d.merchant_code,
                                        'beneficiary_code', d.beneficiary_code,
                                        'switcher_code', d.switcher_code,
                                        'locked', d.locked,
                                        'is_finalized', d.is_finalized,
                                        'is_resolved', d.is_resolved,
                                        'resolved_in_customer_favor', d.resolved_in_customer_favor
                                )
                        )
                 FROM (
                          SELECT *
                          FROM disputes
                          WHERE is_finalized = FALSE
                            AND locked = FALSE
                          ORDER BY created_at ASC
                              LIMIT p_limit
                          OFFSET p_offset
                      ) d),
                '[]'::jsonb
                       ),
               'meta', jsonb_build_object(
                       'total', (SELECT COUNT(*) FROM disputes WHERE is_finalized = FALSE AND locked = FALSE),
                       'limit', p_limit,
                       'offset', p_offset,
                       'has_more', (p_offset + p_limit) < (SELECT COUNT(*) FROM disputes WHERE is_finalized = FALSE AND locked = FALSE),
                       'filtered_by', jsonb_build_object(
                               'is_finalized', FALSE,
                               'locked', FALSE
                                      )
                       )
       ) INTO result;

RETURN result;
END;
$$;