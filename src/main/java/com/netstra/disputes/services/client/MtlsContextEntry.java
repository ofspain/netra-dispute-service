package com.netstra.disputes.services.client;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.RedisHash;
import org.springframework.data.redis.core.index.Indexed;

import java.io.Serializable;
import java.time.Instant;

@Data
@RedisHash("mtls_context")
class MtlsContextEntry implements Serializable {

    //KNOWN
    //@Value("${trust.store.password}")
    //    private String trustStorePassword;
    //    @Value("${trust.store.alias}")
    //    private String trustStoreAlias;
    //
    //    @Value("${trust.store.type}")
    //    private String trustStoreType;
    //
    //
    //    @Value("${trust.key-filename}")
    //    private String trustKeyFileName;
    //
    //    @Value("${key.store-filename}")
    //    private String keyStoreFileName;

    @Id
    @Indexed
    private String id;   // e.g. "BANK_GTB"

    // You don’t want to serialize SSLContext directly, but you can store
    // the cert + password references or resolved secrets here
    private String certPathAlias;     // e.g. vault://certs/client-cert.p12
    private String certPasswordAlias; // e.g. vault://secrets/cert-pass
    private Instant loadedAt;
    private Instant expiresAt; // optional, in case certs rotate
}

//Instead of storing the raw SSLContext (which isn’t serializable and shouldn’t leave memory), you persist the necessary secrets/aliases in Redis.
//
//At runtime:
//
//First call → resolve from Vault, build SSLContext, persist an entry in Redis (so other nodes can benefit).
//
//Next call → check Redis. If entry exists and not expired, resolve cert/password again (from Vault if necessary, but you could also store the resolved secret in Redis with TTL). Build or reuse SSLContext in-memory.
//
//Local nodes still keep a short-lived in-memory cache for fast use, but Redis is the source of truth.

