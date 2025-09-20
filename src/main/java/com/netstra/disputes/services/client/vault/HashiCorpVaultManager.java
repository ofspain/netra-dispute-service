package com.netstra.disputes.services.client.vault;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "vault.provider", havingValue = "hashicorp")
@Qualifier("hashiCorpVault")
public class HashiCorpVaultManager implements VaultManager {

    @Override
    public String getSecret(String vaultPath) {
        // Internally you could create HashiCorpSecretRequest here
        // and call Vault API
        return "hashicorp-secret-for-" + vaultPath;
    }
}

