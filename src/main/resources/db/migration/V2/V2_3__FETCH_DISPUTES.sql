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
    p_date_mark_legit_from TIMESTAMP DEFAULT NULL,
    p_date_mark_legit_to TIMESTAMP DEFAULT NULL,
    p_sort_column TEXT DEFAULT 'created_at',
    p_sort_direction VARCHAR(10) DEFAULT 'ASC'
)
RETURNS JSONB
LANGUAGE plpgsql
AS $$
DECLARE
conditions TEXT[] := ARRAY[]::TEXT[];
    v_order_by TEXT;
    v_where TEXT := '';
    v_result JSONB;
    v_total_count BIGINT;
    v_sql TEXT;
BEGIN
    -- Collect conditions
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
    IF p_date_mark_legit_from IS NOT NULL THEN
        conditions := conditions || format('d.dispute_marked_legit_time >= %L', p_date_mark_legit_from);
END IF;
    IF p_date_mark_legit_to IS NOT NULL THEN
        conditions := conditions || format('d.dispute_marked_legit_time <= %L', p_date_mark_legit_to);
END IF;

    -- Build WHERE clause
    IF array_length(conditions, 1) > 0 THEN
        v_where := 'WHERE ' || array_to_string(conditions, ' AND ');
END IF;

    -- Build ORDER BY safely
    IF p_sort_column ~ '^[a-zA-Z_][a-zA-Z0-9_]*$' THEN
        v_order_by := format('ORDER BY %I %s',
            p_sort_column,
            CASE WHEN upper(p_sort_direction) IN ('ASC', 'DESC')
                 THEN upper(p_sort_direction)
                 ELSE 'ASC' END);
ELSE
        RAISE WARNING 'Invalid sort column: %', p_sort_column;
        v_order_by := 'ORDER BY created_at ASC';
END IF;

    -- Total count query
EXECUTE format('SELECT COUNT(*) FROM disputes d %s', v_where) INTO v_total_count;

-- Main query
v_sql := format($q$
        SELECT jsonb_build_object(
            'data', COALESCE(
                (SELECT jsonb_agg(to_jsonb(d))
                 FROM (
                    SELECT *
                    FROM disputes d
                    %s
                    %s
                    LIMIT %s
                    OFFSET %s
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
