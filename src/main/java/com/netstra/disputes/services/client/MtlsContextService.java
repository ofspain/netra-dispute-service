package com.netstra.disputes.services.client;

import org.springframework.stereotype.Service;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import java.io.FileInputStream;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class MtlsContextService {

    private final MtlsContextRepository repository;
    private final EndpointSecretManager secretManager;

    // Local in-memory cache (fast path), keyed by domainCode
    private final Map<String, SSLContext> localCache = new ConcurrentHashMap<>();

    public MtlsContextService(MtlsContextRepository repository,
                              EndpointSecretManager secretManager) {
        this.repository = repository;
        this.secretManager = secretManager;
    }

    public SSLContext getOrCreateContext(String domainCode, Map<String, String> props) {
        // 1. Fast path: local memory
        if (localCache.containsKey(domainCode)) {
            return localCache.get(domainCode);
        }

        // 2. Check Redis for metadata
        Optional<MtlsContextEntry> entryOpt = repository.findById(domainCode);

        if (entryOpt.isPresent()) {
            MtlsContextEntry entry = entryOpt.get();

            // Expiration check
            if (entry.getExpiresAt() != null && Instant.now().isAfter(entry.getExpiresAt())) {
                repository.deleteById(domainCode);
            } else {
                // Resolve actual secrets from Vault/secret store
                String certPath = secretManager.resolveSecret(entry.getCertPathAlias());
                String certPassword = secretManager.resolveSecret(entry.getCertPasswordAlias());

                SSLContext ctx = buildSslContext(certPath, certPassword);
                localCache.put(domainCode, ctx);
                return ctx;
            }
        }

        // 3. Last resort: load fresh from Vault & persist metadata to Redis
        String certPath = secretManager.resolveSecret(props.get("certPath"));
        String certPassword = secretManager.resolveSecret(props.get("certPassword"));

        SSLContext ctx = buildSslContext(certPath, certPassword);

        MtlsContextEntry newEntry = new MtlsContextEntry();
        newEntry.setId(domainCode);
        newEntry.setCertPathAlias(props.get("certPath"));
        newEntry.setCertPasswordAlias(props.get("certPassword"));
        newEntry.setLoadedAt(Instant.now());
        newEntry.setExpiresAt(Instant.now().plus(Duration.ofHours(12))); // configurable TTL

        repository.save(newEntry);

        localCache.put(domainCode, ctx);

        return ctx;
    }

    private SSLContext buildSslContext(String certPath, String certPassword) {
        try {
            KeyStore keyStore = KeyStore.getInstance("PKCS12");
            try (InputStream is = new FileInputStream(certPath)) {
                keyStore.load(is, certPassword.toCharArray());
            }

            KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(keyStore, certPassword.toCharArray());

            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(kmf.getKeyManagers(), null, new SecureRandom());

            return sslContext;
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize SSLContext for MTLS", e);
        }
    }
}
