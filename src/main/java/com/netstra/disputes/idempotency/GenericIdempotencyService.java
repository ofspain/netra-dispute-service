package com.netstra.disputes.idempotency;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

@Service
@Slf4j
@RequiredArgsConstructor
public class GenericIdempotencyService {

    private final List<IdempotencyKeyResolver> keyResolvers;
    private final IdempotencyRepository repository;
    private final IdempotencyLockManager lockManager;
    private final ApplicationEventPublisher eventPublisher;
    private final IdempotencyMetrics metrics;



    /**
     * Main entry point for any idempotent operation
     */
    public <T, R> IdempotencyResult<R> executeIdempotent(
            IdempotencyRequest originalIdempotencyRequest,
            T operationData,
            Supplier<R> operation,
            Class<R> resultType) {

        long startTime = System.currentTimeMillis();

        // 1. Resolve idempotency key
        String idempotencyKey = resolveIdempotencyKey(originalIdempotencyRequest, operationData);
        log.debug("Resolved idempotency key: {}", idempotencyKey);

        // 2. Check cache first (without lock for performance)
        IdempotencyContext<R> cached = repository.findByKey(idempotencyKey);
        if (cached != null && cached.getStatus() == IdempotencyContext.OperationStatus.SUCCESS) {
            metrics.recordCacheHit();
            long duration = System.currentTimeMillis() - startTime;

            eventPublisher.publishEvent(
                    IdempotencyEvent.duplicateOperation(
                            idempotencyKey,
                            originalIdempotencyRequest.getOperationName(),
                            IdempotencyContext.Source.GENERATED,
                            originalIdempotencyRequest.actorIdentity(),
                            duration
                    )
            );

            return IdempotencyResult.duplicate(
                    cached.getResult(),
                    idempotencyKey,
                    cached.getCreatedAt()
            );
        }

        metrics.recordCacheMiss();

        // 3. Use distributed lock for concurrent safety
        return lockManager.executeWithLock(
                idempotencyKey,
                () -> processWithLock(idempotencyKey, originalIdempotencyRequest, operationData, operation, resultType, startTime),
                5000L,  // 5 second wait for lock
                30000L, // 30 second lease time
                originalIdempotencyRequest.getOperationName(),
                originalIdempotencyRequest.actorIdentity()
        );
    }

    private <T, R> IdempotencyResult<R> processWithLock(
            String idempotencyKey,
            IdempotencyRequest context,
            T operationData,
            Supplier<R> operation,
            Class<R> resultType,
            long startTime) {

        // 4. Check for existing execution (again, after acquiring lock)
        IdempotencyContext<R> existing = repository.findByKey(idempotencyKey);

        if (existing != null) {
            return handleExistingContext(existing, idempotencyKey, context, startTime);
        }

        // 5. Create in-progress context
        IdempotencyContext<R> contextEntity = createInProgressContext(
                idempotencyKey, context, operationData, resultType);
        repository.save(contextEntity);

        try {
            // 6. Execute the operation
            log.info("Executing new idempotent operation: key={}, operation={}",
                    idempotencyKey, context.getOperationName());

            R result = operation.get();
            long duration = System.currentTimeMillis() - startTime;

            // 7. Update context with success
            updateContextWithSuccess(contextEntity, result);
            repository.save(contextEntity);

            // 8. Publish success event
            eventPublisher.publishEvent(
                    IdempotencyEvent.newOperation(
                            idempotencyKey,
                            context.getOperationName(),
                            IdempotencyContext.Source.GENERATED,
                            context.actorIdentity(),
                            duration
                    )
            );

            log.info("Idempotent operation completed successfully: key={}, duration={}ms",
                    idempotencyKey, duration);

            return IdempotencyResult.success(result, idempotencyKey);

        } catch (Exception e) {
            // 9. Update context with failure
            log.error("Idempotent operation failed: key={}, error={}",
                    idempotencyKey, e.getMessage(), e);

            long duration = System.currentTimeMillis() - startTime;

            updateContextWithFailure(contextEntity, e);
            repository.save(contextEntity);

            // Publish error event
            eventPublisher.publishEvent(
                    new IdempotencyEvent(
                            this,
                            idempotencyKey,
                            context.getOperationName(),
                            IdempotencyContext.Source.GENERATED,
                            context.actorIdentity(),
                            IdempotencyEvent.Status.ERROR,
                            false,
                            duration
                    )
            );

            return IdempotencyResult.failure(
                    e.getMessage(),
                    extractErrorCode(e),
                    idempotencyKey
            );
        }
    }

    private <R> IdempotencyResult<R> handleExistingContext(
            IdempotencyContext<R> existing,
            String idempotencyKey,
            IdempotencyRequest context,
            long startTime) {

        long duration = System.currentTimeMillis() - startTime;

        switch (existing.getStatus()) {
            case IN_PROGRESS:
                log.warn("Operation already in progress: key={}", idempotencyKey);

                eventPublisher.publishEvent(
                        IdempotencyEvent.inProgressOperation(
                                idempotencyKey,
                                context.getOperationName(),
                                IdempotencyContext.Source.GENERATED,
                                context.actorIdentity()
                        )
                );

                return IdempotencyResult.inProgress(idempotencyKey);

            case SUCCESS:
                log.info("Returning cached result for duplicate operation: key={}",
                        idempotencyKey);

                eventPublisher.publishEvent(
                        IdempotencyEvent.duplicateOperation(
                                idempotencyKey,
                                context.getOperationName(),
                                IdempotencyContext.Source.GENERATED,
                                context.actorIdentity(),
                                duration
                        )
                );

                return IdempotencyResult.duplicate(
                        existing.getResult(),
                        idempotencyKey,
                        existing.getCreatedAt()
                );

            case FAILED:
                log.info("Returning cached failure for duplicate operation: key={}",
                        idempotencyKey);

                return IdempotencyResult.failure(
                        existing.getErrorDetails(),
                        existing.getErrorCode(),
                        idempotencyKey
                );

            default:
                log.error("Unknown status in idempotency context: key={}, status={}",
                        idempotencyKey, existing.getStatus());

                return IdempotencyResult.failure(
                        "Invalid operation status",
                        "INVALID_STATUS",
                        idempotencyKey
                );
        }
    }

    private String resolveIdempotencyKey(IdempotencyRequest context, Object operationData) {
        if(context.getIdempotencyKey().isPresent()){
            return context.getIdempotencyKey().get();
        }
        // Try all resolvers
        for (IdempotencyKeyResolver resolver : keyResolvers) {
            Optional<String> key = resolver.resolveKey(context);
            if (key.isPresent()) {
                return key.get();
            }
        }

        // Generate from fingerprint as fallback
        String fingerprint = generateFingerprint(operationData);
        String actorId = context.actorIdentity();
        String timeBucket = getTimeBucket();

        return "GEN_" + actorId + "_" + timeBucket + "_" +
                org.springframework.util.DigestUtils
                        .md5DigestAsHex(fingerprint.getBytes())
                        .substring(0, 12);
    }


    public String generateFingerprint(Object operationData) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            String json = mapper.writeValueAsString(operationData);
            return DigestUtils.sha256Hex(json);
        } catch (JsonProcessingException e) {
            return "ERROR_" + System.currentTimeMillis();
        }
    }

    private String getTimeBucket() {
        // Bucket into 10-minute windows
        LocalDateTime now = LocalDateTime.now();
        int minuteBucket = (now.getMinute() / 10) * 10;
        return now.withMinute(minuteBucket)
                .withSecond(0)
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmm"));
    }

    private <R> IdempotencyContext<R> createInProgressContext(
            String idempotencyKey,
            IdempotencyRequest context,
            Object operationData,
            Class<R> resultType) {

        return IdempotencyContext.<R>builder()
                .key(idempotencyKey)
                .operation(context.getOperationName())
                .actorId(context.actorIdentity())
                .actorType(determineActorType(context))
                .fingerprint(generateFingerprint(operationData))
                .status(IdempotencyContext.OperationStatus.IN_PROGRESS)
                .createdAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusHours(24))
                .metadata(context.getParameters())
                .build();
    }

    private <R> void updateContextWithSuccess(IdempotencyContext<R> context, R result) {
        context.setStatus(IdempotencyContext.OperationStatus.SUCCESS);
        context.setResult(result);
        context.setResultSignature(generateResultSignature(result));
        context.setUpdatedAt(LocalDateTime.now());
    }

    private <R> void updateContextWithFailure(IdempotencyContext<R> context, Exception e) {
        context.setStatus(IdempotencyContext.OperationStatus.FAILED);
        context.setErrorDetails(e.getMessage());
        context.setErrorCode(extractErrorCode(e));
        context.setUpdatedAt(LocalDateTime.now());
    }

    private String generateResultSignature(Object result) {
        try {
            String json = new com.fasterxml.jackson.databind.ObjectMapper()
                    .writeValueAsString(result);
            return org.springframework.util.DigestUtils
                    .md5DigestAsHex(json.getBytes());
        } catch (Exception e) {
            return "ERROR_SIGNATURE";
        }
    }

    private String extractErrorCode(Exception e) {
        if (e instanceof IllegalArgumentException) return "INVALID_INPUT";
        if (e instanceof java.lang.NullPointerException) return "NULL_POINTER";
        if (e instanceof org.springframework.dao.DataAccessException) return "DATA_ACCESS_ERROR";
        return "UNKNOWN_ERROR";
    }

    private IdempotencyContext.ActorType determineActorType(IdempotencyRequest context) {
        if (context.getSource() instanceof JobExecutionMetadata) {
            return IdempotencyContext.ActorType.JOB;
        }

        // Check headers for user context
        if (context.getHeaders().containsKey(IdempotencyRequest.IDEM_USER_ID_KEY) ||
                context.getHeaders().containsKey(IdempotencyRequest.IDEM_AUTHORIZATION_KEY)) {
            return IdempotencyContext.ActorType.USER;
        }

        return IdempotencyContext.ActorType.SYSTEM;
    }
}