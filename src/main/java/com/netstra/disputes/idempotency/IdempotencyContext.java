package com.netstra.disputes.idempotency;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

@Builder
@Data
public class IdempotencyContext<T> {
    // Core identifier
    private String key;                    // Unique identifier for the operation
    private IdempotencyRequest.IdempotencyOperation operation;              // Domain-specific operation name
    private Source source;                 // Where it came from

    // Context
    private String actorId;                // Who/what initiated (user, system, service)
    private ActorType actorType;           // USER, SYSTEM, SERVICE, BATCH_JOB
    private String resourceId;             // Optional: resource being acted upon
    private String fingerprint;            // Hash of operation parameters

    // Result storage
    private T result;                      // Generic result object
    private String resultSignature;        // Hash of result for verification
    private String errorDetails;           // If operation failed
    private String errorCode;           // If operation failed
    private OperationStatus status;        // SUCCESS, FAILED, IN_PROGRESS

    //trace

    private String correlationId;                    // Unique identifier for the operation
    private String requestOrigin;
    private String httpMethod;
    private String requestPath;
    private String clientIp;
    private String userAgent;

    // Metadata
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime expiresAt;
    private Map<String, String> metadata;  // Flexible additional data

    public enum ActorType {
        USER,           // Human user via UI/API
        SYSTEM,         // System process (cron, scheduler)
        SERVICE,        // Internal service call
        EXTERNAL_API,   // External system
        JOB,      // Batch processing job
        EVENT_HANDLER,  // Message/event consumer
        WEBHOOK         // Incoming webhook
    }

    public enum Source {
        HEADER,         // From HTTP Idempotency-Key header
        PARAMETER,      // From request parameter
        PROPERTY,       // From message/event property
        GENERATED,      // System-generated
        FINGERPRINT     // Derived from operation hash
    }

    public enum OperationStatus {
        IN_PROGRESS,
        SUCCESS,
        FAILED,
        TIMEOUT
    }
}
