package com.netstra.disputes.idempotency;


import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Wrapper for idempotent operation results
 * @param <T> Type of the result payload
 */
@Data
@Builder
public class IdempotencyResult<T> {

    // Operation status
    private boolean success;
    private boolean duplicate;
    private boolean inProgress;

    // Result data
    private T result;
    private String errorMessage;
    private String errorCode;

    // Idempotency metadata
    private String idempotencyKey;
    private LocalDateTime processedAt;
    private String source;  // "CACHED", "NEW", "IN_PROGRESS"

    // Original context (optional)
    private String originalOperation;
    private LocalDateTime originalProcessedAt;

    /**
     * Static factory for successful new operation
     */
    public static <T> IdempotencyResult<T> success(T result, String idempotencyKey) {
        return IdempotencyResult.<T>builder()
                .success(true)
                .duplicate(false)
                .inProgress(false)
                .result(result)
                .idempotencyKey(idempotencyKey)
                .processedAt(LocalDateTime.now())
                .source("NEW")
                .build();
    }

    /**
     * Static factory for duplicate operation (cached result)
     */
    public static <T> IdempotencyResult<T> duplicate(T cachedResult,
                                                     String idempotencyKey,
                                                     LocalDateTime originalProcessedAt) {
        return IdempotencyResult.<T>builder()
                .success(true)
                .duplicate(true)
                .inProgress(false)
                .result(cachedResult)
                .idempotencyKey(idempotencyKey)
                .processedAt(LocalDateTime.now())
                .source("CACHED")
                .originalProcessedAt(originalProcessedAt)
                .build();
    }

    /**
     * Static factory for operation in progress
     */
    public static <T> IdempotencyResult<T> inProgress(String idempotencyKey) {
        return IdempotencyResult.<T>builder()
                .success(false)
                .duplicate(false)
                .inProgress(true)
                .idempotencyKey(idempotencyKey)
                .processedAt(LocalDateTime.now())
                .source("IN_PROGRESS")
                .errorMessage("Operation is already in progress")
                .errorCode("CONCURRENT_OPERATION")
                .build();
    }

    /**
     * Static factory for failed operation
     */
    public static <T> IdempotencyResult<T> failure(String errorMessage,
                                                   String errorCode,
                                                   String idempotencyKey) {
        return IdempotencyResult.<T>builder()
                .success(false)
                .duplicate(false)
                .inProgress(false)
                .idempotencyKey(idempotencyKey)
                .processedAt(LocalDateTime.now())
                .source("NEW")
                .errorMessage(errorMessage)
                .errorCode(errorCode)
                .build();
    }

    /**
     * Convert to ResponseEntity for HTTP responses
     * TODO: CHANGE TO APP SPECIFIC API RESPONSE
     */
    public org.springframework.http.ResponseEntity<T> toResponseEntity() {
        if (inProgress) {
            return org.springframework.http.ResponseEntity
                    .status(org.springframework.http.HttpStatus.CONFLICT)
                    .header("X-Idempotency-Key", idempotencyKey)
                    .header("X-Idempotency-Status", "in-progress")
                    .body(null);
        }

        if (duplicate) {
            return org.springframework.http.ResponseEntity
                    .status(org.springframework.http.HttpStatus.OK)
                    .header("X-Idempotency-Key", idempotencyKey)
                    .header("X-Idempotency-Status", "cached")
                    .header("X-Original-Processed-At",
                            originalProcessedAt != null ?
                                    originalProcessedAt.toString() : "")
                    .body(result);
        }

        if (success) {
            return org.springframework.http.ResponseEntity
                    .status(org.springframework.http.HttpStatus.CREATED)
                    .header("X-Idempotency-Key", idempotencyKey)
                    .header("X-Idempotency-Status", "processed")
                    .body(result);
        }

        // Failure case
        IdempotencyApiError error = IdempotencyApiError.builder()
                .code(errorCode)
                .message(errorMessage)
                .idempotencyKey(idempotencyKey)
                .build();

        return org.springframework.http.ResponseEntity
                .status(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR)
                .header("X-Idempotency-Key", idempotencyKey)
                .header("X-Idempotency-Status", "failed")
                .body(null);
    }

    /**
     * Helper method to extract result or throw if error
     */
    public T getResultOrThrow() {
        if (inProgress) {
            throw new ConcurrentIdempotencyOperationException(
                    "Operation is already in progress. Idempotency key: " + idempotencyKey, "");
        }

        if (!success) {
            throw new IdempotencyOperationException(errorMessage, errorCode, idempotencyKey);
        }

        return result;
    }
}

