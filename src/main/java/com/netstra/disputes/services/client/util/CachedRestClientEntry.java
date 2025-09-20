package com.netstra.disputes.services.client.util;

import org.springframework.web.client.RestClient;

import java.time.Instant;

//todo: consider to use redis for storage instead of in-mem currently in use
public class CachedRestClientEntry {
    private final RestClient client;
    private final Instant createdAt;
    private final Instant expiresAt;

    private String id;

    public CachedRestClientEntry(RestClient client, Instant expiresAt) {
        this.client = client;
        this.createdAt = Instant.now();
        this.expiresAt = expiresAt;
    }

    public RestClient getClient() {
        return client;
    }

    public boolean isExpired() {
        return expiresAt != null && Instant.now().isAfter(expiresAt);
    }
}
