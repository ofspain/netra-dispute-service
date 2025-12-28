package com.netstra.disputes.idempotency;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.time.LocalDateTime;

/**
 * Event published when idempotency operations occur
 */
@Getter
public class IdempotencyEvent extends ApplicationEvent {

    private final String key;
    private final IdempotencyRequest.IdempotencyOperation operation;
    private final IdempotencyContext.Source source;
    private final String actorId;
    private final Status status;
    private final boolean duplicate;
//    private final LocalDateTime timestamp;
   private final long processingTimeMs;

    public enum Status {
        NEW,              // First time processing
        DUPLICATE,        // Returning cached result
        IN_PROGRESS,      // Concurrent operation detected
        ERROR,           // Operation failed
        EXPIRED,         // Key expired
        LOCK_ACQUIRED,   // Lock acquired for processing
        LOCK_FAILED      // Failed to acquire lock
    }

    public IdempotencyEvent(Object source,
                            String key,
                            IdempotencyRequest.IdempotencyOperation operation,
                            IdempotencyContext.Source sourceType,
                            String actorId,
                            Status status,
                            boolean duplicate,
                            long processingTimeMs) {
        super(source);
        this.key = key;
        this.operation = operation;
        this.source = sourceType;
        this.actorId = actorId;
        this.status = status;
        this.duplicate = duplicate;
        this.processingTimeMs = processingTimeMs;
    }

    public static IdempotencyEvent newOperation(String key,
                                                IdempotencyRequest.IdempotencyOperation operation,
                                                IdempotencyContext.Source source,
                                                String actorId,
                                                long processingTimeMs) {
        return new IdempotencyEvent(
                IdempotencyEvent.class,
                key,
                operation,
                source,
                actorId,
                Status.NEW,
                false,
                processingTimeMs
        );
    }

    public static IdempotencyEvent duplicateOperation(String key,
                                                      IdempotencyRequest.IdempotencyOperation operation,
                                                      IdempotencyContext.Source source,
                                                      String actorId,
                                                      long processingTimeMs) {
        return new IdempotencyEvent(
                IdempotencyEvent.class,
                key,
                operation,
                source,
                actorId,
                Status.DUPLICATE,
                true,
                processingTimeMs
        );
    }

    public static IdempotencyEvent inProgressOperation(String key,
                                                       IdempotencyRequest.IdempotencyOperation operation,
                                                       IdempotencyContext.Source source,
                                                       String actorId) {
        return new IdempotencyEvent(
                IdempotencyEvent.class,
                key,
                operation,
                source,
                actorId,
                Status.IN_PROGRESS,
                false,
                0
        );
    }

    @Override
    public String toString() {
        return String.format(
                "IdempotencyEvent{key='%s', operation='%s', status=%s, duplicate=%s, " +
                        "source=%s, actor='%s', processingTime=%dms}",
                key, operation, status, duplicate, source, actorId, processingTimeMs);
    }
}
