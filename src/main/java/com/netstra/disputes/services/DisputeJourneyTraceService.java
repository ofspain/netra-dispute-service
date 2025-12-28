package com.netstra.disputes.services;


import com.netra.commons.models.Dispute;
import com.netra.commons.models.DisputeJourneyTrace;
import com.netstra.disputes.dao.DisputeJourneyTraceDao;
import com.netstra.disputes.idempotency.IdempotencyContext;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class DisputeJourneyTraceService {

    private final DisputeJourneyTraceDao dao;


    @Transactional
    public DisputeJourneyTrace recordTransition(DisputeJourneyTrace trace) {
        try {
            return dao.insert(trace)
                    .orElseThrow(() -> new IllegalStateException("Insert did not return a record"));
        } catch (DataAccessException e) {
            // If unique violation occurs -> immutability breach
            if (e.getMessage() != null && e.getMessage().contains("already exists")) {
                throw new IllegalStateException("Immutable record already exists for dispute " + trace.getDisputeId());
            }
            throw e; // bubble up other DB exceptions
        }
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
        return dao.findTraces(disputeId, fromDate, toDate, initiatedByCode, fromState, toState, page, size);
    }



}

