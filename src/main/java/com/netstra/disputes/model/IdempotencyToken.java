package com.netstra.disputes.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "idempotency_tokens",
        uniqueConstraints = @UniqueConstraint(columnNames = {"key", "operation"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
public class IdempotencyToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String key;  // Composite key: "key::operation"

    @Column(nullable = false)
    private String operation;

    @Column(nullable = false)
    private String actor;

    @Column(nullable = false)
    private String fingerprint;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime completedAt;
    private String resultType; // "SUCCESS", "ERROR"
    private String errorReason;
}
