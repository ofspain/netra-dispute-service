package com.netstra.disputes.idempotency;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netstra.disputes.config.UtilConfig;
import jakarta.persistence.*;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.springframework.data.annotation.Transient;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * JPA Entity for storing idempotency context in PostgreSQL
 * Optimized with PostgreSQL-specific features:
 * - JSONB for metadata storage
 * - Generated columns
 * - Partitioning support
 * - Full text search
 */
@Entity
@Table(name = "idempotency_store",
        schema = "public",
        indexes = {
                @Index(name = "idx_idempotency_key", columnList = "idempotency_key", unique = true),
                @Index(name = "idx_fingerprint", columnList = "fingerprint"),
                @Index(name = "idx_actor_operation", columnList = "actor_id, operation"),
                @Index(name = "idx_expires_at", columnList = "expires_at"),
                @Index(name = "idx_created_at", columnList = "created_at"),
                @Index(name = "idx_status_created", columnList = "status, created_at"),
                @Index(name = "idx_actor_type_created", columnList = "actor_type, created_at"),
                @Index(name = "idx_gin_metadata", columnList = "metadata"), // GIN index for JSONB
                @Index(name = "idx_ttl_check", columnList = "expires_at, status") // For cleanup queries
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_idempotency_key", columnNames = {"idempotency_key"})
        })
@Data
@Slf4j
public class IdempotencyEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false, nullable = false)
    private Long id;

    /**
     * Business key - the actual idempotency key used in the system
     * Using UUID as primary candidate for sharding/distribution
     */
    @Column(name = "idempotency_key", nullable = false, length = 500)
    private String key;

    /**
     * Operation being performed (e.g., "CREATE_DISPUTE", "PROCESS_REFUND")
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "operation", nullable = false, length = 100)
    private IdempotencyRequest.IdempotencyOperation operation;

    /**
     * Source of the idempotency key
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 50)
    private IdempotencyContext.Source source;

    /**
     * Type of actor performing the operation
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "actor_type", nullable = false, length = 50)
    private IdempotencyContext.ActorType actorType;

    /**
     * Identifier of the actor (user ID, service name, job name, etc.)
     */
    @Column(name = "actor_id", nullable = false, length = 100)
    private String actorId;

    /**
     * Optional resource ID being acted upon
     */
    @Column(name = "resource_id", length = 100)
    private String resourceId;

    /**
     * SHA-256 fingerprint of the request payload
     * Used for duplicate detection when key is not provided
     */
    @Column(name = "fingerprint", length = 64) // SHA-256 produces 64 hex chars
    private String fingerprint;

    /**
     * Fully qualified class name of the result type
     */
    @Column(name = "result_type", length = 500)
    private String resultType;

    /**
     * Serialized result as JSON
     * Using TEXT for unlimited size (PostgreSQL TEXT can store up to 1GB)
     */
    @Lob
    @Column(name = "result_data")
    private String resultData;

    /**
     * Hash of the result for verification
     */
    @Column(name = "result_signature", length = 64)
    private String resultSignature;

    /**
     * Current status of the operation
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private IdempotencyContext.OperationStatus status;

    /**
     * Error details if operation failed
     */
    @Lob
    @Column(name = "error_details")
    private String errorDetails;

    /**
     * Error code for programmatic handling
     */
    @Column(name = "error_code", length = 50)
    private String errorCode;

    /**
     * Metadata as JSONB - PostgreSQL's binary JSON format
     * Provides better performance and indexing capabilities
     */
    @Column(name = "metadata", columnDefinition = "jsonb")
    private String metadata;

    /**
     * Timestamp when the record was created
     * Cannot be updated
     */
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false,
            columnDefinition = "TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP")
    private LocalDateTime createdAt;

    /**
     * Timestamp when the record was last updated
     * Automatically updated on save
     */
    @UpdateTimestamp
    @Column(name = "updated_at",
            columnDefinition = "TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP")
    private LocalDateTime updatedAt;

    /**
     * Timestamp when this record expires and can be cleaned up
     */
    @Column(name = "expires_at", nullable = false,
            columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private LocalDateTime expiresAt;

    /**
     * Correlation ID for tracing across services
     */
    @Column(name = "correlation_id", length = 100)
    private String correlationId;

    /**
     * Request origin (e.g., "web", "mobile", "api", "cron")
     */
    @Column(name = "request_origin", length = 50)
    private String requestOrigin;

    /**
     * HTTP method if applicable (e.g., "POST", "PUT", "DELETE")
     */
    @Column(name = "http_method", length = 10)
    private String httpMethod;

    /**
     * Request path if applicable
     */
    @Column(name = "request_path", length = 500)
    private String requestPath;

    /**
     * Client IP address
     */
    @Column(name = "client_ip", length = 45) // IPv6 requires 45 chars
    private String clientIp;

    /**
     * User agent if applicable
     */
    @Column(name = "user_agent", length = 500)
    private String userAgent;

    /**
     * Time taken to process the operation in milliseconds
     */
    @Column(name = "processing_time_ms")
    private Long processingTimeMs;

    /**
     * Retry count for operations that support retries
     */
    @Column(name = "retry_count", columnDefinition = "INTEGER DEFAULT 0")
    private Integer retryCount = 0;

    /**
     * Flag indicating if this was a duplicate request
     * Computed column in database (see DDL)
     */
    @Column(name = "is_duplicate", columnDefinition = "BOOLEAN DEFAULT FALSE", insertable = false, updatable = false)
    private Boolean isDuplicate = false;

    /**
     * Days until expiration (computed column)
     */
    @Column(name = "days_to_expire", insertable = false, updatable = false)
    private Integer daysToExpire;

    // Transient fields (not persisted)
    @Transient
    private String resultObject;

    @Transient
    private String metadataMap;

    @Transient
    private String deserializedMetadata;

    /**
     * Default constructor for JPA
     */
    public IdempotencyEntity() {
        // Initialize default values
        this.retryCount = 0;
        this.processingTimeMs = 0L;
        this.isDuplicate = false;
    }

    /**
     * Constructor for quick creation
     */
    public IdempotencyEntity(String key, IdempotencyRequest.IdempotencyOperation operation,
                             IdempotencyContext.Source source,
                             String actorId, IdempotencyContext.ActorType actorType) {
        this();
        this.key = key;
        this.operation = operation;
        this.source = source;
        this.actorId = actorId;
        this.actorType = actorType;
        this.createdAt = LocalDateTime.now();
        this.expiresAt = LocalDateTime.now().plusHours(24); // Default 24h TTL
        this.status = IdempotencyContext.OperationStatus.IN_PROGRESS;
    }

    /**
     * Convert to domain object
     */
    @SuppressWarnings("unchecked")
    public <T> IdempotencyContext<T> toDomain(Class<T> resultType) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            T result = null;

            // Deserialize result if present
            if (resultData != null && !resultData.trim().isEmpty()) {
                result = mapper.readValue(resultData, resultType);
            }

            // Build domain context
            IdempotencyContext.IdempotencyContextBuilder<T> builder = IdempotencyContext.<T>builder()
                    .key(this.key)
                    .operation(this.operation)
                    .source(this.source)
                    .actorId(this.actorId)
                    .actorType(this.actorType)
                    .resourceId(this.resourceId)
                    .fingerprint(this.fingerprint)
                    .result(result)
                    .resultSignature(this.resultSignature)
                    .errorDetails(this.errorDetails)
                    .errorCode(this.errorCode)
                    .status(this.status)
                    .createdAt(this.createdAt)
                    .updatedAt(this.updatedAt)
                    .expiresAt(this.expiresAt)
                    .metadata(parseMetadata())
                    .correlationId(this.correlationId)
                    .requestOrigin(this.requestOrigin)
                    .httpMethod(this.httpMethod)
                    .requestPath(this.requestPath)
                    .clientIp(this.clientIp)
                    .userAgent(this.userAgent);

            return builder.build();

        } catch (IOException e) {
            log.error("Failed to deserialize result data for key: {}", key, e);
            throw new RuntimeException("Failed to deserialize idempotency context", e);
        }
    }

    /**
     * Convert from domain object
     */
    public static <T> IdempotencyEntity fromDomain(IdempotencyContext<T> context) {
        IdempotencyEntity entity = new IdempotencyEntity();

        // Copy basic fields
        entity.setKey(context.getKey());
        entity.setOperation(context.getOperation());
        entity.setSource(context.getSource());
        entity.setActorType(context.getActorType());
        entity.setActorId(context.getActorId());
        entity.setResourceId(context.getResourceId());
        entity.setFingerprint(context.getFingerprint());
        entity.setStatus(context.getStatus());
        entity.setErrorDetails(context.getErrorDetails());
        entity.setErrorCode(context.getErrorCode());
        entity.setCreatedAt(context.getCreatedAt());
        entity.setExpiresAt(context.getExpiresAt());
        entity.setMetadata(serializeMetadata(context.getMetadata()));

        if (context.getCorrelationId() != null) {
            entity.setCorrelationId(context.getCorrelationId());
        }

        // Serialize result
        if (context.getResult() != null) {
            try {
                ObjectMapper mapper = new ObjectMapper();
                entity.setResultData(mapper.writeValueAsString(context.getResult()));
                entity.setResultType(context.getResult().getClass().getName());
                entity.setResultSignature(context.getResultSignature());
            } catch (IOException e) {
                log.error("Failed to serialize result for key: {}", context.getKey(), e);
                throw new RuntimeException("Failed to serialize idempotency result", e);
            }
        }

        // Set updated timestamp
        entity.setUpdatedAt(LocalDateTime.now());

        return entity;
    }

    /**
     * Parse metadata JSON string to Map
     */
    public Map<String, String> parseMetadata() {
        if (metadata == null || metadata.trim().isEmpty() || metadata.equals("null")) {
            return new HashMap<>();
        }else{
            return UtilConfig.convertObjectToClass(new TypeReference<Map<String, String>>() {}, metadata);
        }
    }

    /**
     * Get metadata value by key
     */
    public String getMetadataValue(String key) {
        return parseMetadata().get(key);
    }

    /**
     * Check if metadata contains key
     */
    public boolean hasMetadataKey(String key) {
        return parseMetadata().containsKey(key);
    }

    /**
     * Serialize metadata map to JSON string
     */
    private static String serializeMetadata(Map<String, String> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return null;
        }

        try {
            ObjectMapper mapper = new ObjectMapper();
            return mapper.writeValueAsString(metadata);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize metadata: {}", metadata, e);
            return null;
        }
    }

    /**
     * Check if the record is expired
     */
    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }

    /**
     * Check if the record can be cleaned up
     * (expired and not recently updated)
     */
    public boolean canBeCleanedUp() {
        return isExpired() &&
                (updatedAt == null ||
                        updatedAt.isBefore(LocalDateTime.now().minusHours(1)));
    }

    /**
     * Mark as success with result
     */
    public <T> void markAsSuccess(T result, String resultSignature) {
        this.status = IdempotencyContext.OperationStatus.SUCCESS;
        this.resultSignature = resultSignature;
        this.updatedAt = LocalDateTime.now();

        try {
            ObjectMapper mapper = new ObjectMapper();
            this.resultData = mapper.writeValueAsString(result);
            this.resultType = result.getClass().getName();
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize result for key: {}", key, e);
            throw new RuntimeException("Failed to serialize result", e);
        }
    }

    /**
     * Mark as failed with error
     */
    public void markAsFailed(String errorMessage, String errorCode) {
        this.status = IdempotencyContext.OperationStatus.FAILED;
        this.errorDetails = errorMessage;
        this.errorCode = errorCode;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Increment retry count
     */
    public void incrementRetryCount() {
        this.retryCount = (this.retryCount == null) ? 1 : this.retryCount + 1;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Set processing time
     */
    public void setProcessingTime(long startTimeMillis) {
        this.processingTimeMs = System.currentTimeMillis() - startTimeMillis;
    }

    /**
     * Generate a human-readable string for logging
     */
    public String toLogString() {
        return String.format(
                "IdempotencyEntity{key='%s', operation='%s', actor='%s:%s', status=%s, created=%s, expires=%s}",
                key, operation, actorType, actorId, status,
                createdAt != null ? createdAt.toString() : "null",
                expiresAt != null ? expiresAt.toString() : "null"
        );
    }

    /**
     * Create a defensive copy
     */
    public IdempotencyEntity copy() {
        IdempotencyEntity copy = new IdempotencyEntity();
        copy.id = this.id;
        copy.key = this.key;
        copy.operation = this.operation;
        copy.source = this.source;
        copy.actorType = this.actorType;
        copy.actorId = this.actorId;
        copy.resourceId = this.resourceId;
        copy.fingerprint = this.fingerprint;
        copy.resultType = this.resultType;
        copy.resultData = this.resultData;
        copy.resultSignature = this.resultSignature;
        copy.status = this.status;
        copy.errorDetails = this.errorDetails;
        copy.errorCode = this.errorCode;
        copy.metadata = this.metadata;
        copy.createdAt = this.createdAt;
        copy.updatedAt = this.updatedAt;
        copy.expiresAt = this.expiresAt;
        copy.correlationId = this.correlationId;
        copy.requestOrigin = this.requestOrigin;
        copy.httpMethod = this.httpMethod;
        copy.requestPath = this.requestPath;
        copy.clientIp = this.clientIp;
        copy.userAgent = this.userAgent;
        copy.processingTimeMs = this.processingTimeMs;
        copy.retryCount = this.retryCount;
        copy.isDuplicate = this.isDuplicate;
        copy.daysToExpire = this.daysToExpire;
        return copy;
    }
}