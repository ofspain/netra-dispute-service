package com.netstra.disputes.services.client.vault;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "vault.provider", havingValue = "aws")
@Qualifier("awsVault")
public class AwsSecretsManagerVault implements VaultManager {
    @Override
    public String getSecret(String vaultPath) {
        // Internally you could create AwsSecretRequest here
        // and call AWS Secrets Manager
        return "aws-secret-for-" + vaultPath;
    }
}

