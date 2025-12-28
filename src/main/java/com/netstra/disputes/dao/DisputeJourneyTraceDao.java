package com.netstra.disputes.dao;

import com.netra.commons.models.DisputeJourneyTrace;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.*;

@Repository
public class DisputeJourneyTraceDao {

    private final JdbcClient jdbcClient;

    public DisputeJourneyTraceDao(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    /**
     * Calls the Postgres immutable insert function and returns the inserted record.
     */
    public Optional<DisputeJourneyTrace> insert(DisputeJourneyTrace trace) {

        int paramCount = 14; // or however many params you have
        String placeholders = String.join(", ", Collections.nCopies(paramCount, "?"));

        String sql = "SELECT * FROM insert_dispute_journey_trace(" + placeholders + ")";


        return jdbcClient
                .sql(sql)
                .param(1, trace.getDisputeId())
                .param(2, trace.getFromState().name())
                .param(3, trace.getToState().name())
                .param(4, trace.getTransitionTime())
                .param(5, trace.getInitiatedBy())
//                .param(6, trace.getInitiatedByCode())
//                .param(7, trace.getInitiatedById())
//                .param(8, trace.getReason())
//                .param(9, trace.getApplicationChannel())
//                .param(10, trace.getCurrentHash())
//                .param(11, trace.getPreviousHash())
//                .param(12, trace.getDigitalSignature())
//                .param(13, trace.getAptosTxnHash())
//                .param(14, trace.getAptosEventRef())
                .query(DisputeJourneyTrace.class)
                .optional();
    }

    public Page<DisputeJourneyTrace> findTraces(
            Long disputeId,
            LocalDateTime fromDate,
            LocalDateTime toDate,
            String initiatedByCode,
            String fromState,
            String toState,
            int page,
            int size
    ) {
        if (disputeId == null) {
            throw new IllegalArgumentException("disputeId is required");
        }

        // Base SQL
        StringBuilder sql = new StringBuilder("SELECT * FROM dispute_journey_traces WHERE dispute_id = :disputeId");
        StringBuilder countSql = new StringBuilder("SELECT COUNT(*) FROM dispute_journey_traces WHERE dispute_id = :disputeId");

        Map<String, Object> params = new HashMap<>();
        params.put("disputeId", disputeId);

        // Conditional filters
        if (fromDate != null) {
            sql.append(" AND transition_time >= :fromDate");
            countSql.append(" AND transition_time >= :fromDate");
            params.put("fromDate", fromDate);
        }
        if (toDate != null) {
            sql.append(" AND transition_time <= :toDate");
            countSql.append(" AND transition_time <= :toDate");
            params.put("toDate", toDate);
        }
        if (initiatedByCode != null && !initiatedByCode.isBlank()) {
            sql.append(" AND initiated_by_code = :initiatedByCode");
            countSql.append(" AND initiated_by_code = :initiatedByCode");
            params.put("initiatedByCode", initiatedByCode);
        }
        if (fromState != null && !fromState.isBlank()) {
            sql.append(" AND from_state = :fromState");
            countSql.append(" AND from_state = :fromState");
            params.put("fromState", fromState);
        }
        if (toState != null && !toState.isBlank()) {
            sql.append(" AND to_state = :toState");
            countSql.append(" AND to_state = :toState");
            params.put("toState", toState);
        }

        // Add ordering and pagination
        sql.append(" ORDER BY transition_time ASC LIMIT :limit OFFSET :offset");
        params.put("limit", size);
        params.put("offset", page * size);

        // Execute count query
        Long total = jdbcClient.sql(countSql.toString())
                .params(params)
                .query(Long.class)
                .single();

        // Execute main paged query
        List<DisputeJourneyTrace> traces = jdbcClient.sql(sql.toString())
                .params(params)
                .query(DisputeJourneyTrace.class)
                .list();

        // Wrap in Spring Page
        return new PageImpl<>(traces, PageRequest.of(page, size), total);
    }

    /*
    TO MITIGATE ROUND TRIP TO DB...classic solution

    ⚙️ 3. Cached or Estimated Count

For immutable data (like your trace table!), caching is super safe — once a dispute’s trace set is final, its count doesn’t change.
You could cache the count per dispute_id in memory or Redis.

⚙️ 4. Hybrid Strategy

Use the COUNT(*) for the first page only, then stop counting after page 1 — most users never scroll that far anyway.
     */
}

