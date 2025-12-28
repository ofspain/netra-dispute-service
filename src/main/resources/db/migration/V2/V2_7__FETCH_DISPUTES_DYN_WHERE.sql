CREATE OR REPLACE FUNCTION fetch_dispute_dynamic_where(
    p_limit INTEGER DEFAULT 50,
    p_offset INTEGER DEFAULT 0,
    p_where_clause TEXT DEFAULT NULL  -- pass dynamic WHERE conditions here
)
RETURNS JSONB
LANGUAGE plpgsql
AS $$
DECLARE
v_where TEXT := '';
    v_order_by TEXT := 'ORDER BY d.created_at DESC'; -- default ordering
    v_sql TEXT;
    v_total_count BIGINT;
    v_result JSONB;
BEGIN
    -- Use the provided WHERE clause if any
    IF p_where_clause IS NOT NULL AND length(trim(p_where_clause)) > 0 THEN
        v_where := 'WHERE ' || p_where_clause;
END IF;

    -- ------------------------------
    -- Count total matching rows
    -- ------------------------------
EXECUTE format('SELECT COUNT(*) FROM disputes d %s', v_where)
    INTO v_total_count;

-- ------------------------------
-- Main query: dispute + minimal transaction info
-- ------------------------------
v_sql := format($q$
        SELECT jsonb_build_object(
            'data', COALESCE(
                (SELECT jsonb_agg(to_jsonb(d))
                 FROM (
                     SELECT
                         d.id,
                         d.log_code,
                         d.transaction_date,
                         d.transaction_amount,
                         d.current_state,
                         d.previous_state,
                         d.dispute_mode,
                         d.locked,
                         d.is_finalized,
                         d.is_resolved,
                         d.resolved_in_customer_favor,
                         d.created_via,
                         d.issuer_code,
                         d.acquirer_code,
                         d.merchant_code,
                         d.beneficiary_code,
                         d.switcher_code,
                         d.disputant_identity_uuid,
                         d.disputant_type,
                         d.disputant_domain_code,
                         d.plaintiff_institution_code,
                         d.defendant_institution_code,
                         d.on_us_transaction,
                         oti.transaction_instrument AS oti_instrument,
                         oti.payment_rail AS oti_rail
                     FROM disputes d
                     LEFT JOIN other_transaction_infos oti
                        ON oti.id = d.transaction_info_id
                     %s
                     %s
                     LIMIT %s OFFSET %s
                 ) d),
                '[]'::jsonb
            ),
            'meta', jsonb_build_object(
                'total', %s,
                'limit', %s,
                'offset', %s,
                'has_more', (%s + %s) < %s
            )
        )
    $q$, v_where, v_order_by, p_limit, p_offset,
         v_total_count, p_limit, p_offset,
         p_offset, p_limit, v_total_count);

EXECUTE v_sql INTO v_result;
RETURN v_result;
END;
$$;
