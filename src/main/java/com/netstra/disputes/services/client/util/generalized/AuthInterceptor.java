package com.netstra.disputes.services.client.util.generalized;

import com.netra.commons.models.endpoint.*;
import com.netstra.disputes.services.client.vault.VaultManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Base64;

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
            case MTLS, NONE -> {}
        }
        return execution.execute(request, body);
    }
    private void applyApiKey(HttpRequest request, ApiKeyAuth config) {
        String key = config.getApiKey(); // resolve from vault if needed
        if (config.isAsQueryParam() && config.getQueryParamName() != null) {
            // add as query param
            String url = request.getURI().toString();
            url += (url.contains("?") ? "&" : "?") + config.getQueryParamName() + "=" + key;
            request.getHeaders().set(HttpHeaders.LOCATION, url); // trick: may need wrapper for URI override
        } else {
            String headerValue = decorate(config.getPrefix(), config.getPrefixSeparator(), key, config.getSuffix(), config.getSuffixSeparator());
            request.getHeaders().set(config.getHeaderName(), headerValue);
        }
    }

    private void applyBearer(HttpRequest request, BearerTokenAuth config) {
        String token = config.getToken(); // resolve vault if needed
        String value = config.getPrefix() + " " + token;
        request.getHeaders().set(config.getHeaderName(), value);
    }

    private void applyBasic(HttpRequest request, BasicAuth config) {
        String username = config.getUsername();
        String password = config.getPassword(); // resolve vault if needed
        String auth = username + ":" + password;
        String encoded = Base64.getEncoder().encodeToString(auth.getBytes());
        request.getHeaders().set(config.getHeaderName(), "Basic " + encoded);
    }

    private void applyCustomSignature(HttpRequest request, byte[] body, CustomSignatureAuth config) {
        // Implement HMAC or whatever signature your API expects
        String signature = computeSignature(body, config);
        request.getHeaders().set("X-Signature", signature);
    }

    private String decorate(String prefix, String prefixSep, String key, String suffix, String suffixSep) {
        StringBuilder sb = new StringBuilder();
        if (prefix != null) sb.append(prefix).append(prefixSep);
        sb.append(key);
        if (suffix != null) sb.append(suffixSep).append(suffix);
        return sb.toString();
    }

    private String computeSignature(byte[] body, CustomSignatureAuth config) {
        // implement your algorithm using body + config.getKey() + config.getParameters()
        return "dummy-signature"; // placeholder
    }
}

