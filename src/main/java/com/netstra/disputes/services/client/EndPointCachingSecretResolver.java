package com.netstra.disputes.services.client;

import com.netstra.disputes.services.client.vault.VaultManager;
import org.springframework.stereotype.Service;

@Service
public class EndPointCachingSecretResolver implements EndpointSecretManager {

    private final EndpointSecretRepository secretRepository;
    private final VaultManager vaultManager;

    public EndPointCachingSecretResolver(EndpointSecretRepository secretRepository,
                                         VaultManager vaultManager) {
        this.secretRepository = secretRepository;
        this.vaultManager = vaultManager;
    }

    @Override
    public String resolveSecret(String vaultPath) {
        return secretRepository.findById(vaultPath)
                .map(EndpointSecret::getSecret)
                .orElseGet(() -> {
                    String secret = vaultManager.getSecret(vaultPath);

                    EndpointSecret cache = new EndpointSecret();
                    cache.setId(vaultPath);
                    cache.setSecret(secret);
                    secretRepository.save(cache);

                    return secret;
                });
    }
}
