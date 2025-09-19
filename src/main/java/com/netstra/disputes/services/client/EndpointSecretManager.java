package com.netstra.disputes.services.client;

/**
 * SecretManager: abstraction to resolve secret aliases or return literal values.
 * Implementations: VaultSecretManager, AwsSecretsManager, LocalEncryptedStore.
 */
public interface EndpointSecretManager {
    /**
     * Given a reference (either actual value or alias like vault://...), return the secret value.
     * If the input is a literal (no special schema), just return it.
     */
    String resolveSecret(String valueOrAlias);
}
