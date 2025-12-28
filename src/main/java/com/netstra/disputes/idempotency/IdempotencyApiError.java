package com.netstra.disputes.idempotency;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * API Error DTO
 */
@Data
@Builder
@AllArgsConstructor
class IdempotencyApiError {
    private String code;
    private String message;
    private String idempotencyKey;
    private LocalDateTime timestamp;

    public IdempotencyApiError() {
        this.timestamp = LocalDateTime.now();
    }

    public IdempotencyApiError(String code, String message, String idempotencyKey) {
        this.code = code;
        this.message = message;
        this.idempotencyKey = idempotencyKey;
        this.timestamp = LocalDateTime.now();
    }
}
