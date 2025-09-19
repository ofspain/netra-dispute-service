package com.netstra.disputes.services.client;


import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.RedisHash;
import org.springframework.data.redis.core.index.Indexed;

import java.io.Serializable;
import java.time.Instant;

/**
 * Simple token cache for bearer tokens (per clientId or per endpoint).
 * Production: add expiry, refresh-before-expiry, thread-safety, locking to avoid stampede.
 * todo: this can be a great use case for RedisHash
 */
@Data
@RedisHash
class EndpointSecret implements Serializable {
    @Id
    @Indexed
    private String id;
    private String secret;
    private Instant expiresAt;
}