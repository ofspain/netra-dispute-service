package com.netstra.disputes.idempotency;

/**
 * Exception for concurrent operations
 */
class ConcurrentIdempotencyOperationException extends RuntimeException {
    private final String idempotencyKey;

    public ConcurrentIdempotencyOperationException(String message, String idempotencyKey) {
        super(message);
        this.idempotencyKey = idempotencyKey;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }
}
