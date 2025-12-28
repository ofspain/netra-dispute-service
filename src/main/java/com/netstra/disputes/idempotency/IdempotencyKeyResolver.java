package com.netstra.disputes.idempotency;

import java.util.Optional;

public interface IdempotencyKeyResolver {
    /**
     * Resolve idempotency key from various sources
     */
    Optional<String> resolveKey(IdempotencyRequest request);

    /**
     * Determine actor type based on request source
     */
    IdempotencyContext.ActorType determineActorType(IdempotencyRequest request);
}
