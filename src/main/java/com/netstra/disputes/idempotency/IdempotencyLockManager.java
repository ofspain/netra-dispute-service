package com.netstra.disputes.idempotency;

import lombok.RequiredArgsConstructor;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@Component
@RequiredArgsConstructor
public class IdempotencyLockManager {

    private final RedissonClient redissonClient;
    public static String IDEMPOTENCY_LOCK_KEY_PREFIX = "idempotency:";

    public <T> T executeWithLock(String lockKey, Supplier<T> operation,
                                 long waitTime, long leaseTime,
                                 IdempotencyRequest.IdempotencyOperation idemOperation, String actorId) {
        RLock lock = redissonClient.getLock(IDEMPOTENCY_LOCK_KEY_PREFIX + lockKey);

        try {
            boolean acquired = lock.tryLock(waitTime, leaseTime, TimeUnit.MILLISECONDS);
            if (!acquired) {
                throw new ConcurrentIdempotencyOperationException(
                        "Could not acquire lock for idempotency key: " + lockKey,lockKey);
            }

            return operation.get();

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ConcurrentIdempotencyOperationException("Interrupted while waiting for lock", lockKey);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
}
