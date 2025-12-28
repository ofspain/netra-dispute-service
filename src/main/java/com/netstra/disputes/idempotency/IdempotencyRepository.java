package com.netstra.disputes.idempotency;

import java.time.LocalDateTime;
import java.util.List;

public interface IdempotencyRepository {
    <T> IdempotencyContext<T> findByKey(String key);
    <T> void save(IdempotencyContext<T> context);
    void deleteExpired(LocalDateTime before);

    // Optional: Find by fingerprint for deduplication
    <T> List<IdempotencyContext<T>> findByFingerprintAndTimeWindow(
            String fingerprint,
            String actorId,
            LocalDateTime from,
            LocalDateTime to);
}
