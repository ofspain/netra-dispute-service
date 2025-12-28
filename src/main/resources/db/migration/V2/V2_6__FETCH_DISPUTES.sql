CREATE OR REPLACE FUNCTION fetch_disputes(
    p_limit INTEGER DEFAULT 50,
    p_offset INTEGER DEFAULT 0,
    p_is_finalized BOOLEAN DEFAULT NULL,
    p_is_resolved BOOLEAN DEFAULT NULL,
    p_resolved_in_customer_favor BOOLEAN DEFAULT NULL,
    p_locked BOOLEAN DEFAULT NULL,
    p_current_state VARCHAR(50) DEFAULT NULL,
    p_previous_state VARCHAR(50) DEFAULT NULL,
    p_disputant_identity_uuid VARCHAR(100) DEFAULT NULL,
    p_disputant_domain_code VARCHAR(50) DEFAULT NULL,
    p_issuer_code VARCHAR(50) DEFAULT NULL,
    p_acquirer_code VARCHAR(50) DEFAULT NULL,
    p_merchant_code VARCHAR(50) DEFAULT NULL,
    p_beneficiary_code VARCHAR(50) DEFAULT NULL,
    p_switcher_code VARCHAR(50) DEFAULT NULL,
    p_created_via VARCHAR(50) DEFAULT NULL,
    p_date_from TIMESTAMP DEFAULT NULL,
    p_date_to TIMESTAMP DEFAULT NULL,
    p_sort_column TEXT DEFAULT 'created_at',
    p_sort_direction VARCHAR(10) DEFAULT 'DESC'
)
RETURNS JSONB
LANGUAGE plpgsql
AS $$
DECLARE
conditions TEXT[] := ARRAY[]::TEXT[];
    v_where TEXT := '';
    v_order_by TEXT;
    v_sql TEXT;
    v_total_count BIGINT;
    v_result JSONB;
BEGIN
    -- ------------------------------
    -- Collect filter conditions
    -- ------------------------------
    IF p_is_finalized IS NOT NULL THEN
        conditions := conditions || format('d.is_finalized = %L', p_is_finalized);
END IF;
    IF p_is_resolved IS NOT NULL THEN
        conditions := conditions || format('d.is_resolved = %L', p_is_resolved);
END IF;
    IF p_resolved_in_customer_favor IS NOT NULL THEN
        conditions := conditions || format('d.resolved_in_customer_favor = %L', p_resolved_in_customer_favor);
END IF;
    IF p_locked IS NOT NULL THEN
        conditions := conditions || format('d.locked = %L', p_locked);
END IF;
    IF p_current_state IS NOT NULL THEN
        conditions := conditions || format('d.current_state = %L', p_current_state);
END IF;
    IF p_previous_state IS NOT NULL THEN
        conditions := conditions || format('d.previous_state = %L', p_previous_state);
END IF;
    IF p_disputant_identity_uuid IS NOT NULL THEN
        conditions := conditions || format('d.disputant_identity_uuid = %L', p_disputant_identity_uuid);
END IF;
    IF p_disputant_domain_code IS NOT NULL THEN
        conditions := conditions || format('d.disputant_domain_code = %L', p_disputant_domain_code);
END IF;
    IF p_issuer_code IS NOT NULL THEN
        conditions := conditions || format('d.issuer_code = %L', p_issuer_code);
END IF;
    IF p_acquirer_code IS NOT NULL THEN
        conditions := conditions || format('d.acquirer_code = %L', p_acquirer_code);
END IF;
    IF p_merchant_code IS NOT NULL THEN
        conditions := conditions || format('d.merchant_code = %L', p_merchant_code);
END IF;
    IF p_beneficiary_code IS NOT NULL THEN
        conditions := conditions || format('d.beneficiary_code = %L', p_beneficiary_code);
END IF;
    IF p_switcher_code IS NOT NULL THEN
        conditions := conditions || format('d.switcher_code = %L', p_switcher_code);
END IF;
    IF p_created_via IS NOT NULL THEN
        conditions := conditions || format('d.created_via = %L', p_created_via);
END IF;
    IF p_date_from IS NOT NULL THEN
        conditions := conditions || format('d.created_at >= %L', p_date_from);
END IF;
    IF p_date_to IS NOT NULL THEN
        conditions := conditions || format('d.created_at <= %L', p_date_to);
END IF;

    -- Build WHERE clause
    IF array_length(conditions, 1) > 0 THEN
        v_where := 'WHERE ' || array_to_string(conditions, ' AND ');
END IF;

    -- Build ORDER BY safely
    IF p_sort_column ~ '^[a-zA-Z_][a-zA-Z0-9_]*$' THEN
        v_order_by := format('ORDER BY %I %s',
                             p_sort_column,
                             CASE WHEN upper(p_sort_direction) IN ('ASC','DESC') THEN upper(p_sort_direction) ELSE 'DESC' END);
ELSE
        RAISE WARNING 'Invalid sort column: %', p_sort_column;
        v_order_by := 'ORDER BY created_at DESC';
END IF;

    -- Total count
EXECUTE format('SELECT COUNT(*) FROM disputes d %s', v_where)
    INTO v_total_count;

-- Main query: include light transaction summary
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
