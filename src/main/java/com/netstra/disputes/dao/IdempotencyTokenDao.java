package com.netstra.disputes.dao;

import com.netstra.disputes.model.IdempotencyToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface IdempotencyTokenDao extends JpaRepository<IdempotencyToken, Long> {

    Optional<IdempotencyToken> findByKeyAndOperation(String key, String Operation);
}
