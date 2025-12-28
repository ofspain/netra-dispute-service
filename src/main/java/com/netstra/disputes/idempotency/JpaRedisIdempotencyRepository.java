package com.netstra.disputes.idempotency;


import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Component
@Slf4j
@RequiredArgsConstructor
public class JpaRedisIdempotencyRepository implements IdempotencyRepository {

    private static final String CACHE_PREFIX = "idempotency:";
    private static final long CACHE_TTL_HOURS = 1; // Cache for 1 hour

    private final IdempotencyJpaRepository jpaRepository;
    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(readOnly = true)
    public <T> IdempotencyContext<T> findByKey(String key) {
        // 1. Try Redis cache first
        Optional<IdempotencyContext<T>> cached = findInCache(key);
        if (cached.isPresent()) {
            log.debug("Cache hit for idempotency key: {}", key);
            return cached.get();
        }

        // 2. Fallback to database
        log.debug("Cache miss for idempotency key: {}, querying database", key);
        IdempotencyEntity entity = jpaRepository.findByKey(key);

        if (entity == null) {
            return null;
        }

        // 3. Deserialize from entity
        try {
            Class<T> resultType = determineResultType(entity.getResultType());
            IdempotencyContext<T> context = entity.toDomain(resultType);

            // 4. Cache the result
            cacheContext(key, context);

            return context;
        } catch (Exception e) {
            log.error("Failed to deserialize idempotency context for key: {}", key, e);
            return null;
        }
    }

    @Override
    @Transactional
    public <T> void save(IdempotencyContext<T> context) {
        try {
            // 1. Convert to entity
            IdempotencyEntity entity = IdempotencyEntity.fromDomain(context);

            // 2. Save to database
            jpaRepository.save(entity);

            // 3. Update cache
            cacheContext(context.getKey(), context);

            log.debug("Saved idempotency context for key: {}", context.getKey());

        } catch (Exception e) {
            log.error("Failed to save idempotency context for key: {}", context.getKey(), e);
            throw new RuntimeException("Failed to save idempotency context", e);
        }
    }

    @Override
    @Transactional
    public void deleteExpired(LocalDateTime before) {
        try {
            // 1. Find expired records
            List<IdempotencyEntity> expired = jpaRepository.findAll()
                    .stream()
                    .filter(e -> e.getExpiresAt().isBefore(before))
                    .collect(Collectors.toList());

            // 2. Clear cache for expired records
            expired.forEach(entity ->
                    redisTemplate.delete(CACHE_PREFIX + entity.getKey()));

            // 3. Delete from database
            int deleted = jpaRepository.deleteExpired(before);

            log.info("Deleted {} expired idempotency records (expired before {})",
                    deleted, before);

        } catch (Exception e) {
            log.error("Failed to delete expired idempotency records", e);
            throw new RuntimeException("Failed to clean up expired idempotency records", e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public <T> List<IdempotencyContext<T>> findByFingerprintAndTimeWindow(
            String fingerprint,
            String actorId,
            LocalDateTime from,
            LocalDateTime to) {

        try {
            // Query database
            List<IdempotencyEntity> entities = jpaRepository
                    .findByFingerprintAndActorAndTimeWindow(fingerprint, actorId, from, to);

            // Convert to domain objects
            return entities.stream()
                    .map(entity -> {
                        try {
                            Class<T> resultType = determineResultType(entity.getResultType());
                            return entity.toDomain(resultType);
                        } catch (Exception e) {
                            log.warn("Failed to deserialize entity for fingerprint: {}",
                                    fingerprint, e);
                            return null;
                        }
                    })
                    .filter(context -> context != null)
                    .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("Failed to query idempotency records by fingerprint: {}",
                    fingerprint, e);
            throw new RuntimeException("Failed to query idempotency records", e);
        }
    }

    /**
     * Additional utility methods
     */
    @Transactional(readOnly = true)
    public boolean exists(String key) {
        // Check cache first
        if (Boolean.TRUE.equals(redisTemplate.hasKey(CACHE_PREFIX + key))) {
            return true;
        }

        return jpaRepository.existsByKey(key);
    }

    @Transactional
    public void delete(String key) {
        // Clear cache
        redisTemplate.delete(CACHE_PREFIX + key);

        // Delete from database
        IdempotencyEntity entity = jpaRepository.findByKey(key);
        if (entity != null) {
            jpaRepository.delete(entity);
            log.debug("Deleted idempotency record for key: {}", key);
        }
    }

    @Transactional(readOnly = true)
    public long count() {
        return jpaRepository.count();
    }

    @Transactional(readOnly = true)
    public long countExpired() {
        return jpaRepository.countExpired();
    }

    @Transactional
    public void updateExpiration(String key, LocalDateTime newExpiresAt) {
        IdempotencyEntity entity = jpaRepository.findByKey(key);
        if (entity != null) {
            entity.setExpiresAt(newExpiresAt);
            jpaRepository.save(entity);

            // Update cache
            redisTemplate.delete(CACHE_PREFIX + key);

            log.debug("Updated expiration for key: {} to {}", key, newExpiresAt);
        }
    }

    /**
     * Cache operations
     */
    private <T> Optional<IdempotencyContext<T>> findInCache(String key) {
        try {
            ValueOperations<String, String> ops = redisTemplate.opsForValue();
            String cachedJson = ops.get(CACHE_PREFIX + key);

            if (cachedJson == null) {
                return Optional.empty();
            }

            // Deserialize from JSON
            IdempotencyContext<T> context = objectMapper.readValue(
                    cachedJson,
                    objectMapper.getTypeFactory().constructParametricType(
                            IdempotencyContext.class,
                            Object.class // Generic type will be resolved when needed
                    )
            );

            return Optional.of(context);

        } catch (Exception e) {
            log.warn("Failed to deserialize cached idempotency context for key: {}", key, e);
            // Clear corrupted cache
            redisTemplate.delete(CACHE_PREFIX + key);
            return Optional.empty();
        }
    }

    private <T> void cacheContext(String key, IdempotencyContext<T> context) {
        try {
            // Calculate TTL based on expiration time
            long ttlSeconds = ChronoUnit.SECONDS.between(
                    LocalDateTime.now(),
                    context.getExpiresAt()
            );

            // Don't cache if already expired or expiring soon
            if (ttlSeconds <= 0) {
                return;
            }

            // Cap TTL at 1 hour for cache
            ttlSeconds = Math.min(ttlSeconds, CACHE_TTL_HOURS * 3600);

            // Serialize to JSON
            String json = objectMapper.writeValueAsString(context);

            // Store in Redis
            ValueOperations<String, String> ops = redisTemplate.opsForValue();
            ops.set(CACHE_PREFIX + key, json, ttlSeconds, TimeUnit.SECONDS);

            log.debug("Cached idempotency context for key: {} with TTL {} seconds",
                    key, ttlSeconds);

        } catch (Exception e) {
            log.warn("Failed to cache idempotency context for key: {}", key, e);
            // Don't throw - caching is optional
        }
    }

    /**
     * Clear cache for a specific key
     */
    public void clearCache(String key) {
        redisTemplate.delete(CACHE_PREFIX + key);
        log.debug("Cleared cache for idempotency key: {}", key);
    }

    /**
     * Clear entire idempotency cache
     */
    public void clearAllCache() {
        String pattern = CACHE_PREFIX + "*";
        redisTemplate.delete(redisTemplate.keys(pattern));
        log.info("Cleared all idempotency cache entries");
    }

    /**
     * Helper method to determine result type from class name
     */
    @SuppressWarnings("unchecked")
    private <T> Class<T> determineResultType(String className) {
        if (className == null || className.isEmpty()) {
            return (Class<T>) Object.class;
        }

        try {
            return (Class<T>) Class.forName(className);
        } catch (ClassNotFoundException e) {
            log.warn("Result class not found: {}, defaulting to Object", className);
            return (Class<T>) Object.class;
        }
    }
}
