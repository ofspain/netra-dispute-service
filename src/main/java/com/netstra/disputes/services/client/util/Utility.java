package com.netstra.disputes.services.client.util;

import com.netra.commons.enums.DomainType;
import com.netra.commons.models.endpoint.*;
import com.netra.commons.util.BasicUtil;
import com.netra.commons.util.CryptoUtils;
import com.netra.commons.util.UriBuilderUtil;
import com.netstra.disputes.services.client.vault.VaultManager;
import lombok.experimental.UtilityClass;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.random.RandomGenerator;

import org.springframework.http.*;
import java.util.*;

@UtilityClass
public class Utility {

    public ResolvedRequest prepareEndpointRequest(
            EndpointDetail detail,
            EndpointConfig config,
            Map<String, String> requestBodyContext,
            ParamsDTO paramsDTO,
            VaultManager manager
    ) {
        // --- Extract request params ---
        Map<String, String> pathParams = paramsDTO.pathParams();
        Map<String, String> queryParams = new HashMap<>(paramsDTO.queryParams());
        Map<DynamicHeader, String> dynamicHeaderValues = paramsDTO.dynamicHeaderValues();

        // --- Resolve HTTP method ---
        HttpMethod method = convertMethod(detail.getMethod());

        // --- Build base headers ---
        HttpHeaders headers = buildHeaders(
                detail.getHeaders(),
                config.getSecurity().getAuthConfigs(),
                dynamicHeaderValues,
                manager
        );

        // --- Resolve request body ---
        boolean hasBody = BasicUtil.validString(detail.getRequestBodyTemplate());
        String requestBody = null;
        if (hasBody) {
            requestBody = UriBuilderUtil.resolveRequestBody(detail.getRequestBodyTemplate(), requestBodyContext);
        }

        // === 1️⃣ Encryption ===
        EncryptionConfig encryption = config.getSecurity().getEncryption();
        if (hasBody && encryption != null && encryption.getType() != EncryptionConfig.EncryptionType.NONE) {
            String encryptionSecret = manager.getSecret(encryption.getEncryptionKey());
            EncryptionConfig.EncryptedPayload encPayload =
                    encryption.encryptBody(requestBody, encryptionSecret);
            requestBody = encPayload.getCombined();

            // Place IV in headers if any dynamic header requires it
            if (encryption.getAadHeaders() != null) {
                for (EncryptionConfig.AadHeader aadHeader : encryption.getAadHeaders()) {
                    if (aadHeader.isDynamic() && "HEADER".equalsIgnoreCase(aadHeader.getName())) {
                        headers.set(aadHeader.getName(), encPayload.getIv());
                    } else if (aadHeader.isDynamic() && "QUERY".equalsIgnoreCase(aadHeader.getName())) {
                        queryParams.put(aadHeader.getName(), encPayload.getIv());
                    }
                }
            }
        }

        // === 2️⃣ Signing (CustomSignatureAuth) ===
        Optional<CustomSignatureAuth> customAuthOpt = config.getSecurity().getAuthConfigs().stream()
                .filter(auth -> auth instanceof CustomSignatureAuth)
                .map(auth -> (CustomSignatureAuth) auth)
                .findFirst();

        if (customAuthOpt.isPresent()) {
            CustomSignatureAuth custom = customAuthOpt.get();

            String canonical = custom.buildCanonicalString(
                    method.name(),
                    detail.getUrl(),
                    UriBuilderUtil.buildQueryString(queryParams),
                    requestBody,
                    headers.toSingleValueMap()
            );

            String signingSecret = manager.getSecret(custom.getKey());
            String signature = custom.sign(canonical, signingSecret);

            if (custom.getPlacement() == CustomSignatureAuth.SignaturePlacement.HEADER) {
                headers.set(custom.getSignatureName(), signature);
            } else if (custom.getPlacement() == CustomSignatureAuth.SignaturePlacement.QUERY_PARAM) {
                queryParams.put(custom.getSignatureName(), signature);
            }
        }

        // --- Final resolved URL ---
        String resolvedUrl = UriBuilderUtil.resolveUrl(
                config.getNetwork().getBaseUrl(),
                detail.getUrl(),
                pathParams,
                queryParams
        );

        // --- Build HTTP entity ---
        HttpEntity<?> entity;
        if (hasBody) {
            headers.setContentType(MediaType.APPLICATION_JSON);
            entity = new HttpEntity<>(requestBody, headers);
        } else {
            entity = new HttpEntity<>(headers);
        }

        return new ResolvedRequest(resolvedUrl, method, entity);
    }




    private HttpMethod convertMethod(EndpointDetail.HTTPMethod method) {
        return method == EndpointDetail.HTTPMethod.POST ? HttpMethod.POST : HttpMethod.GET;
    }

    public HttpHeaders buildHeaders(List<StaticHeader> staticHeaders,
                                    List<AuthConfig> authConfigs,
                                    Map<DynamicHeader, String> dynamicHeaderValues,
                                    VaultManager vaultManager) {
        HttpHeaders headers = new HttpHeaders();

        // Static headers
        if (staticHeaders != null) {
            for (StaticHeader sh : staticHeaders) {
                String value = sh.getValue();
                if (sh.isSecret()) {
                    value = vaultManager.getSecret(value);
                }
                headers.add(sh.getName(), value);
            }
        }

        // Auth headers (except CustomSignatureAuth — handled later)
        for (AuthConfig authConfig : authConfigs) {
            if (authConfig instanceof ApiKeyAuth apiKeyAuth && !apiKeyAuth.isAsQueryParam()) {
                String secret = vaultManager.getSecret(apiKeyAuth.getApiKey());
                String val = apiKeyAuth.buildValue(secret);
                headers.add(apiKeyAuth.getHeaderName(), val);

            } else if (authConfig instanceof BearerTokenAuth bearer) {
                String secret = vaultManager.getSecret(bearer.getToken());
                headers.add(bearer.getHeaderName(), bearer.buildValue(secret));

            } else if (authConfig instanceof BasicAuth basic) {
                String user = basic.getUsername();
                String pass = vaultManager.getSecret(basic.getPassword());
                String value = basic.buildValue(user, pass);
                headers.add(basic.getHeaderName(), value);
            }
        }

        // Dynamic headers
        if (dynamicHeaderValues != null) {
            for (Map.Entry<DynamicHeader, String> entry : dynamicHeaderValues.entrySet()) {
                DynamicHeader dynHeader = entry.getKey();
                String value = entry.getValue();

                if (dynHeader.isRequired() && value == null) {
                    throw new IllegalArgumentException(
                            "Required dynamic header '" + dynHeader.getName() + "' is missing"
                    );
                }

                if (value != null) {
                    headers.add(dynHeader.getName(), value);
                }
            }
        }

        return headers;
    }

    public static String calculateCachedRestClientId(EndpointConfigIdentity identity) {
        Long ownerId = identity.ownerId();
        String domainCode = identity.domainCode();
        DomainType type = identity.type();
        Long selfId = identity.selfId();

        String uuid = selfId + ":" + ":" + domainCode + ":" + type.name() + ownerId;
        return uuid;
    }

    /**
     * Decrypts an encrypted response payload using the provided EncryptionConfig.
     *
     * @param responseBody the encrypted payload (expected format: "iv:ciphertext")
     * @param encryptionConfig the encryption configuration to use
     * @param manager VaultManager to resolve the encryption key
     * @return the decrypted plaintext response
     */
    public String processEncryptedResponse(String responseBody, EncryptionConfig encryptionConfig, VaultManager manager) {

        if (encryptionConfig == null || encryptionConfig.getType() == EncryptionConfig.EncryptionType.NONE) {
            // No encryption configured, return as-is
            return responseBody;
        }

        if (responseBody == null || !responseBody.contains(":")) {
            throw new IllegalArgumentException("Invalid encrypted response format. Expected 'iv:ciphertext'");
        }

        String[] parts = responseBody.split(":", 2);
        String iv = parts[0];
        String ciphertext = parts[1];

        String encryptionKey = manager.getSecret(encryptionConfig.getEncryptionKey());

        // Default to BASE64, can be extended to support HEX if needed
        return encryptionConfig.decryptBody(ciphertext, iv, encryptionKey);
    }

}

