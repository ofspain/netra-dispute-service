package com.netstra.disputes.services.client.util;

import com.netra.commons.enums.DomainType;
import com.netra.commons.models.endpoint.*;
import com.netra.commons.util.BasicUtil;
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
            Map<String,String> requestBodyContext,
            ParamsDTO paramsDTO,
            VaultManager manager
    ) {
        Map<String, String> pathParams = paramsDTO.pathParams();
        Map<String, String> queryParams = paramsDTO.queryParams();
        Map<DynamicHeader, String> dynamicHeaderValues = paramsDTO.dynamicHeaderValues();

        // 1. Handle API key in query params
        for (AuthConfig authConfig : config.getSecurity().getAuthConfigs()) {
            if (authConfig instanceof ApiKeyAuth) {
                ApiKeyAuth apiKeyAuth = (ApiKeyAuth) authConfig;
                if (apiKeyAuth.isAsQueryParam()) {
                    String paramName = apiKeyAuth.getQueryParamName();
                    String secretVal = manager.getSecret(apiKeyAuth.getApiKey());
                    String value = apiKeyAuth.buildValue(secretVal);
                    queryParams.put(paramName, value);
                }
            }
        }

        // 2. Resolve HTTP method (URL will be built at the very end)
        HttpMethod method = convertMethod(detail.getMethod());

        // 3. Build base headers
        HttpHeaders headers = buildHeaders(
                detail.getHeaders(),
                config.getSecurity().getAuthConfigs(),
                dynamicHeaderValues,
                manager
        );

        // 4. Resolve request body if any
        boolean hasBody = BasicUtil.validString(detail.getRequestBodyTemplate());
        String requestBody = null;
        if (hasBody) {
            requestBody = UriBuilderUtil.resolveRequestBody(detail.getRequestBodyTemplate(), requestBodyContext);
        }

        // 5. Handle Custom Signature Auth (may mutate queryParams, headers, and body)
        for (AuthConfig authConfig : config.getSecurity().getAuthConfigs()) {
            if (authConfig instanceof CustomSignatureAuth) {
                CustomSignatureAuth custom = (CustomSignatureAuth) authConfig;

                String actualBodyToSend = requestBody;
                String bodyForSigning = requestBody;

                // Encrypt body if required
                if (hasBody && custom.isEncryptionEnabled()) {
                    String encryptionSecret = manager.getSecret(custom.getEncryptionKey());
                    CustomSignatureAuth.EncryptedPayload enc = custom.encryptBody(requestBody, encryptionSecret);

                    actualBodyToSend = enc.getCombined();
                    bodyForSigning = custom.isSignEncryptedBody() ? enc.getCombined() : requestBody;

                    // handle IV placement
                    String ivPlacement = custom.getParameters().get("ivPlacement");
                    if ("HEADER".equalsIgnoreCase(ivPlacement)) {
                        headers.set("X-IV", enc.getIv());
                    } else if ("QUERY".equalsIgnoreCase(ivPlacement)) {
                        queryParams.put("iv", enc.getIv());
                    }
                }

                // Build canonical string for signing
                String canonical = custom.buildCanonicalString(
                        method.name(),
                        detail.getUrl(),
                        UriBuilderUtil.buildQueryString(queryParams),
                        bodyForSigning,
                        headers.toSingleValueMap()
                );

                // Generate signature
                String signingSecret = manager.getSecret(custom.getKey());
                String signature = custom.sign(canonical, signingSecret);

                // Place signature
                if (custom.getPlacement() == CustomSignatureAuth.SignaturePlacement.HEADER) {
                    headers.set(custom.getSignatureName(), signature);
                } else if (custom.getPlacement() == CustomSignatureAuth.SignaturePlacement.QUERY_PARAM) {
                    queryParams.put(custom.getSignatureName(), signature);
                }

                // Update body with encrypted/plain
                requestBody = actualBodyToSend;
            }
        }

        // 6. Build final resolved URL (only once, after all possible mutations)
        String resolvedUrl = UriBuilderUtil.resolveUrl(
                config.getNetwork().getBaseUrl(),
                detail.getUrl(),
                pathParams,
                queryParams
        );

        // 7. Build entity
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
                String user = vaultManager.getSecret(basic.getUsername());
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

        String uuid = ownerId + ":" + ":" + domainCode + ":" + type.name();
        return uuid;
    }

    public String processResponseForEncrypted(String responseBody, CustomSignatureAuth custom, VaultManager manager) {
        if (custom.isEncryptionEnabled()) {
            // assume response is "iv:ciphertext"
            String[] parts = responseBody.split(":");
            String iv = parts[0];
            String ciphertext = parts[1];

            String key = manager.getSecret(custom.getEncryptionKey());
            return custom.decryptBody(ciphertext, iv, key);
        }
        return responseBody;
    }
}

