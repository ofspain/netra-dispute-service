package com.netstra.disputes.controllers;


import com.netra.commons.models.DisputeJourneyTrace;
import com.netstra.disputes.services.DisputeJourneyTraceService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/v1/disputes/traces")
@RequiredArgsConstructor
public class DisputeJourneyTraceController {

    private final DisputeJourneyTraceService service;


    @PostMapping
    public ResponseEntity<?> recordTransition(@RequestBody DisputeJourneyTrace trace) {
        try {
            DisputeJourneyTrace created = service.recordTransition(trace);
            return ResponseEntity.ok(created);
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping
    public ResponseEntity<Page<DisputeJourneyTrace>> findTraces(
            @RequestParam Long disputeId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fromDate,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime toDate,
            @RequestParam(required = false) String initiatedByCode,
            @RequestParam(required = false) String fromState,
            @RequestParam(required = false) String toState,
            Pageable pageable
    ) {
        Page<DisputeJourneyTrace> traces = service.findTraces(
                disputeId,
                fromDate,
                toDate,
                initiatedByCode,
                fromState,
                toState,
                pageable.getPageNumber(),
                pageable.getPageSize()
        );
        return ResponseEntity.ok(traces);
    }

}

