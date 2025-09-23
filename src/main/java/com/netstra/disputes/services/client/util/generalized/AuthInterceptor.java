package com.netstra.disputes.services.client.util.generalized;

import com.netra.commons.models.endpoint.*;
import com.netstra.disputes.services.client.vault.VaultManager;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Interceptor for applying authentication to HTTP requests.
 * Handles API_KEY, BEARER, BASIC, and CUSTOM_SIGNATURE.
 *
 * Note: Encryption of request body is handled in the executor layer, not here.
 */
public class AuthInterceptor implements ClientHttpRequestInterceptor {

    private final AuthConfig authConfig;
    private final VaultManager vaultManager;

    public AuthInterceptor(AuthConfig authConfig, VaultManager vaultManager) {
        this.authConfig = authConfig;
        this.vaultManager = vaultManager;
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
        switch (authConfig.getAuthType()) {
            case API_KEY -> applyApiKey(request, (ApiKeyAuth) authConfig);
            case BEARER -> applyBearer(request, (BearerTokenAuth) authConfig);
            case BASIC -> applyBasic(request, (BasicAuth) authConfig);
            case CUSTOM_SIGNATURE -> applyCustomSignature(request, body, (CustomSignatureAuth) authConfig);
            case MTLS, NONE -> { /* nothing to do */ }
        }
        return execution.execute(request, body);
    }

    // ==================== Auth Type Handlers ====================

    private void applyApiKey(HttpRequest request, ApiKeyAuth config) {
        String key = vaultManager.getSecret(config.getApiKey());
        if (config.isAsQueryParam() && config.getQueryParamName() != null) {
            String url = request.getURI().toString();
            url += (url.contains("?") ? "&" : "?") + config.getQueryParamName() + "=" + key;
            // Note: URL override may need wrapper if Spring doesn't allow setting LOCATION
            request.getHeaders().set(HttpHeaders.LOCATION, url);
        } else {
            String headerValue = decorate(config.getPrefix(), config.getPrefixSeparator(), key,
                    config.getSuffix(), config.getSuffixSeparator());
            request.getHeaders().set(config.getHeaderName(), headerValue);
        }
    }

    private void applyBearer(HttpRequest request, BearerTokenAuth config) {
        String token = vaultManager.getSecret(config.getToken());
        String value = (config.getPrefix() != null ? config.getPrefix() + " " : "") + token;
        request.getHeaders().set(config.getHeaderName(), value);
    }

    private void applyBasic(HttpRequest request, BasicAuth config) {
        String username = config.getUsername();
        String password = vaultManager.getSecret(config.getPassword());
        String auth = username + ":" + password;
        String encoded = Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));
        request.getHeaders().set(config.getHeaderName(), "Basic " + encoded);
    }

    private void applyCustomSignature(HttpRequest request, byte[] body, CustomSignatureAuth config) {
        // Signing uses canonical string logic from CustomSignatureAuth
        String bodyStr = body != null ? new String(body, StandardCharsets.UTF_8) : "";

        // Build canonical string based on request info
        String canonical = config.buildCanonicalString(
                request.getMethod().name(),
                request.getURI().getPath(),
                request.getURI().getQuery() != null ? request.getURI().getQuery() : "",
                bodyStr,
                request.getHeaders().toSingleValueMap()
        );

        // Resolve secret from vault
        String signingSecret = vaultManager.getSecret(config.getKey());

        // Generate signature
        String signature = config.sign(canonical, signingSecret);

        // Apply signature according to placement
        if (config.getPlacement() == CustomSignatureAuth.SignaturePlacement.HEADER) {
            request.getHeaders().set(config.getSignatureName(), signature);
        } else if (config.getPlacement() == CustomSignatureAuth.SignaturePlacement.QUERY_PARAM) {
            // Append signature to query string (may need wrapper)
            String url = request.getURI().toString();
            url += (url.contains("?") ? "&" : "?") + config.getSignatureName() + "=" + signature;
            request.getHeaders().set(HttpHeaders.LOCATION, url);
        }
    }

    // ==================== Helpers ====================

    private String decorate(String prefix, String prefixSep, String key, String suffix, String suffixSep) {
        StringBuilder sb = new StringBuilder();
        if (prefix != null) sb.append(prefix).append(prefixSep);
        sb.append(key);
        if (suffix != null) sb.append(suffixSep).append(suffix);
        return sb.toString();
    }
}
