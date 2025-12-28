package com.netstra.disputes.idempotency;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
@Slf4j
@RequiredArgsConstructor
public class IdempotencyMetrics {

    private final MeterRegistry meterRegistry;

    @EventListener
    public void handleIdempotencyEvent(IdempotencyEvent event) {
        // Record metrics
        Counter counter = Counter.builder("idempotency.operations.total")
                .tag("operation", event.getOperation().name())
                .tag("source", event.getSource().name())
                .tag("status", event.getStatus().name())
                .tag("key_type", event.getKey())
                .tag("duplicate", String.valueOf(event.isDuplicate()))
                .register(meterRegistry);

        counter.increment();

        // Record processing time if applicable
        if (event.getProcessingTimeMs() > 0) {
            Timer timer = Timer.builder("idempotency.operation.duration")
                    .tag("operation", event.getOperation().name())
                    .tag("status", event.getStatus().name())
                    .register(meterRegistry);

            timer.record(event.getProcessingTimeMs(), TimeUnit.MILLISECONDS);
        }

        // Structured logging
        log.info("Idempotency operation processed: {}", event);

        // Additional metrics for specific statuses
        if (event.getStatus() == IdempotencyEvent.Status.DUPLICATE) {
            Counter.builder("idempotency.operations.duplicate")
                    .tag("operation", event.getOperation().name())
                    .register(meterRegistry)
                    .increment();
        }

        if (event.getStatus() == IdempotencyEvent.Status.IN_PROGRESS) {
            Counter.builder("idempotency.operations.concurrent")
                    .tag("operation", event.getOperation().name())
                    .register(meterRegistry)
                    .increment();
        }
    }

    // Additional monitoring methods
    public void recordCacheHit() {
        Counter.builder("idempotency.cache.hits")
                .register(meterRegistry)
                .increment();
    }

    public void recordCacheMiss() {
        Counter.builder("idempotency.cache.misses")
                .register(meterRegistry)
                .increment();
    }

    public void recordLockAcquisition(boolean success, long durationMs) {
        String status = success ? "success" : "failure";
        Counter.builder("idempotency.lock.acquisition")
                .tag("status", status)
                .register(meterRegistry)
                .increment();

        if (success) {
            Timer.builder("idempotency.lock.acquisition.duration")
                    .register(meterRegistry)
                    .record(durationMs, TimeUnit.MILLISECONDS);
        }
    }
}