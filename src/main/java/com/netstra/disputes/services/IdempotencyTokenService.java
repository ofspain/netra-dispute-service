package com.netstra.disputes.services;

import com.netra.commons.requests.CreateDisputeRequest;
import com.netra.commons.util.SecureHashingUtil;
import com.netstra.disputes.dao.IdempotencyTokenDao;
import com.netstra.disputes.exceptions.IdempotencyKeyCollisionException;
import com.netstra.disputes.model.IdempotencyContext;
import com.netstra.disputes.model.IdempotencyToken;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

//todo: find out a more robust and deterministic way to generate idem key from client

@Service
@Slf4j
@Transactional
@RequiredArgsConstructor
public class IdempotencyTokenService {
    private final IdempotencyTokenDao idempotencyTokenDao;
    private final JdbcTemplate jdbcTemplate;

    public IdempotencyContext validateAndRecord(IdempotencyContext context) {
        String compositeKey = createCompositeKey(context.getKey(), context.getOperation());

        try {
            // Atomic insert with conflict detection
            boolean inserted = jdbcTemplate.update(
                    "INSERT INTO idempotency_tokens (key, operation, actor, fingerprint, created_at) " +
                            "VALUES (?, ?, ?, ?, ?) ON CONFLICT (key, operation) DO NOTHING",
                    compositeKey, context.getOperation(), context.getActor(),
                    context.getFingerprint(), LocalDateTime.now()
            ) > 0;

            if (!inserted) {
                // Conflict occurred - fetch existing record
                IdempotencyToken existing = idempotencyTokenDao.findByKeyAndOperation(compositeKey, context.getOperation().name())
                        .orElseThrow(() -> new IllegalStateException("Inconsistent idempotency state"));

                // Check if this is a true replay (same fingerprint) or a different request
                boolean isReplay = existing.getFingerprint().equals(context.getFingerprint());

                if (!isReplay) {
                    // Same idempotency key but different request - potential bug or malicious activity
                    log.warn("Idempotency key collision: key={}, operation={}, existingFingerprint={}, newFingerprint={}",
                            compositeKey, context.getOperation(), existing.getFingerprint(), context.getFingerprint());
                    throw new IdempotencyKeyCollisionException(
                            "Idempotency key already used with different request payload");
                }

                // It's a replay - mark it as such
                context.setReplay(true);
                log.info("Idempotent replay detected: key={}, operation={}", compositeKey, context.getOperation());
            } else {
                // First-time execution
                context.setReplay(false);
                log.debug("Idempotency token recorded: key={}, operation={}", compositeKey, context.getOperation());
            }

            return context;

        } catch (DataIntegrityViolationException ex) {
            // Race condition - treat as duplicate
            log.debug("Concurrent idempotency token creation: key={}", compositeKey);
            context.setReplay(true);
            return context;
        }
    }




    private String createCompositeKey(String key, IdempotencyContext.IdemOperation operation) {
        return String.format("%s::%s", key, operation.name());
    }

}
