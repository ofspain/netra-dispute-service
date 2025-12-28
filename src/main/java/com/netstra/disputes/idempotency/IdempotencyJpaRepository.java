package com.netstra.disputes.idempotency;


import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface IdempotencyJpaRepository extends JpaRepository<IdempotencyEntity, Long> {

    IdempotencyEntity findByKey(String key);

    @Query("SELECT e FROM IdempotencyEntity e WHERE e.fingerprint = :fingerprint " +
            "AND e.actorId = :actorId " +
            "AND e.createdAt >= :from AND e.createdAt <= :to " +
            "ORDER BY e.createdAt DESC")
    List<IdempotencyEntity> findByFingerprintAndActorAndTimeWindow(
            @Param("fingerprint") String fingerprint,
            @Param("actorId") String actorId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    @Modifying
    @Query("DELETE FROM IdempotencyEntity e WHERE e.expiresAt < :before")
    int deleteExpired(@Param("before") LocalDateTime before);

    @Query("SELECT COUNT(e) FROM IdempotencyEntity e WHERE e.expiresAt < CURRENT_TIMESTAMP")
    long countExpired();

    @Query("SELECT e FROM IdempotencyEntity e WHERE e.actorId = :actorId " +
            "AND e.operation = :operation " +
            "AND e.status = 'SUCCESS' " +
            "ORDER BY e.createdAt DESC")
    List<IdempotencyEntity> findByActorAndOperation(
            @Param("actorId") String actorId,
            @Param("operation") String operation);

    @Query("SELECT e FROM IdempotencyEntity e WHERE e.key IN :keys")
    List<IdempotencyEntity> findByKeys(@Param("keys") List<String> keys);

    boolean existsByKey(String key);
}
