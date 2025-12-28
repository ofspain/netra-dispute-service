package com.netstra.disputes.idempotency;

/**
 * Exception for idempotency failures
 */
class IdempotencyOperationException extends RuntimeException {
    private final String errorCode;
    private final String idempotencyKey;

    public IdempotencyOperationException(String message, String errorCode, String idempotencyKey) {
        super(message);
        this.errorCode = errorCode;
        this.idempotencyKey = idempotencyKey;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }
}
