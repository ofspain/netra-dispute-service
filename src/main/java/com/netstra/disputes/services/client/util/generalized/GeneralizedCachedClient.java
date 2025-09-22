package com.netstra.disputes.services.client.util.generalized;

import org.springframework.web.client.RestTemplate;

import java.time.Instant;

public record GeneralizedCachedClient(RestTemplate client, Instant expiry) {
    boolean isExpired() { return Instant.now().isAfter(expiry); }
    RestTemplate getClient() { return client; }
}
