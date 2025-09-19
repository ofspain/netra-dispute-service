package com.netstra.disputes.services.client;

import com.netra.commons.models.EndpointConfig;
import com.netra.commons.trace.AuditLogger;
import com.netra.commons.util.*;
import com.netstra.disputes.services.client.util.ResolvedRequest;
import com.netstra.disputes.services.client.util.ParamsDTO;
import com.netstra.disputes.services.client.util.Utility;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import javax.net.ssl.SSLContext;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
public class EndpointExecutorService {

    private final MeterRegistry meterRegistry;
    private final EndpointSecretManager secretResolver;
    private final AuditLogger auditLogger;
    private final MtlsContextService mtlsContextService; // 🔑 inject service


    //todo: Shall we test this on a stormy endpoint and sail for a sample config JSON + insert command?

    public <T> T executeRequest(
            RestClient client,
            EndpointConfig config,
            boolean isMultiple,
            Map<String, String> pathParams,
            Map<String, String> queryParams,
            Map<String, String> dynamicHeaderValues,
            Map<String, String> requestBodyContext,
            ParameterizedTypeReference<T> responseType
    ) {
        ParamsDTO paramsDTO = new ParamsDTO(pathParams, queryParams, dynamicHeaderValues);

        ResolvedRequest dto = Utility.prepareEndpointRequest(config, isMultiple, requestBodyContext, paramsDTO);

        HttpMethod method = dto.method();
        String resolvedUrl = dto.resolvedUrl();
        HttpEntity<?> entity = dto.entity();

        HttpHeaders headers = entity.getHeaders();
        String bodyContent = entity.getBody().toString();
        boolean hasBody = null != bodyContent;

        // Start timer
        Timer.Sample sample = Timer.start(meterRegistry);
        long start = System.currentTimeMillis();
        try {
            var builder = client.method(method)
                    .uri(resolvedUrl)
                    .headers(h -> h.addAll(headers));

            RestClient.ResponseSpec response;
            if (hasBody) {
                response = builder.body(bodyContent).retrieve();
            } else {
                response = builder.retrieve();
            }

            T result = response.body(responseType);

            auditLogger.logSuccess(config.getDomainCode(), method, resolvedUrl, headers, bodyContent, result, System.currentTimeMillis() - start);
            sample.stop(meterRegistry.timer("external_call", "domain", config.getDomainCode(), "status", "success"));
            return result;

        } catch (Exception e) {
            auditLogger.logFailure(config.getDomainCode(), method, resolvedUrl, headers, bodyContent, e, System.currentTimeMillis() - start);
            sample.stop(meterRegistry.timer("external_call", "domain", config.getDomainCode(), "status", "failure"));
            throw e;
        }
    }




    public <T> T executeRequestWithResilience(
            RestClient client,
            EndpointConfig config,
            boolean isMultiple,
            Map<String, String> pathParams,
            Map<String, String> queryParams,
            Map<String, String> dynamicHeaderValues,
            Map<String, String> requestBodyContext,
            ParameterizedTypeReference<T> responseType,
            Class<T> responseClass
    ) {


        ParamsDTO paramsDTO = new ParamsDTO(pathParams, queryParams, dynamicHeaderValues);

        ResolvedRequest dto = Utility.prepareEndpointRequest(config, isMultiple, requestBodyContext, paramsDTO);

        HttpMethod method = dto.method();
        String resolvedUrl = dto.resolvedUrl();
        HttpEntity<?> entity = dto.entity();

        HttpHeaders headers = entity.getHeaders();
        Object body = entity.getBody();
        boolean hasBody = null != body;

        RestClient.RequestBodySpec request = client
                .method(method)
                .uri(resolvedUrl)
                .headers(h -> h.addAll(headers));

        Supplier<T> execute = () -> {
            if (hasBody) {
                return request.body(body).retrieve().body(responseType);
            } else {
                return request.retrieve().body(responseType);
            }
        };

        ResilienceRegistryResolver resolver = new ResilienceRegistryResolver();
        Supplier<T> safeSupplier = resolver.decorateWithRetryAndFallback(
                config.getDomainCode() + "_" + (isMultiple ? "multi" : "single"),
                execute,
                config,
                responseClass
        );

        return safeSupplier.get();
    }


    private List<EndpointConfig.EndpointHeader> enrichWithAuthHeader(EndpointConfig config, List<EndpointConfig.EndpointHeader> headers) {
        if (!config.isRequiresAuth()) {
            return headers;
        }
        List<EndpointConfig.EndpointHeader> result = new ArrayList<>(headers != null ? headers : List.of());

        EndpointConfig.AuthConfig authConfig = config.getAuthConfig();
        Map<String, String> props = authConfig.getProperties();

        switch (authConfig.getType()) {
            case BEARER_TOKEN -> {
                // Expecting props → { "clientId", "clientSecret", "tokenUrl" }
                // In real impl: call token endpoint and cache/reuse token
                String token = secretResolver.resolveSecret(props.get("accessToken"));
                String authHeaderName = props.getOrDefault("headerName", "Authorization");
                if (token != null && !token.isBlank()) {
                    result.add(new EndpointConfig.EndpointHeader(authHeaderName, "Bearer " + token, false));
                }
            }
            case BASIC -> {
                // Expecting props → { "username", "password" }
                String username = props.get(Constants.AUTHTYPE_USERNAME_NAME);
                String password = secretResolver.resolveSecret(props.get(Constants.AUTHTYPE_PASSWORD_NAME));
                if (username != null && password != null) {
                    String base64Creds = Base64.getEncoder()
                            .encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
                String authHeaderName = props.getOrDefault(Constants.AUTHTYPE_AUTHORIZATION_NAME, Constants.AUTHTYPE_AUTHORIZATION_DEFAULT);

                    result.add(new EndpointConfig.EndpointHeader(authHeaderName, "Basic " + base64Creds, false));
                }
            }
            case API_KEY -> {
                // Expecting props → { "headerName": "X-API-KEY", "apiKey": "xxx" }
                String authHeaderName = props.getOrDefault(Constants.AUTHTYPE_AUTHORIZATION_NAME, "X-API-KEY");

                String apiKey = secretResolver.resolveSecret(props.get(Constants.AUTHTYPE_APIKEY_NAME));
                if (apiKey != null) {
                    result.add(new EndpointConfig.EndpointHeader(authHeaderName, apiKey, false));
                }
            }
            case MTLS -> {
                // Initialize SSLContext (only once per domain)
                SSLContext sslContext = mtlsContextService.getOrCreateContext(config.getDomainCode(), props);

                // No headers are added for mTLS
            }
            default -> {
                // Do nothing for NONE or unhandled types
            }
        }
        return result;
    }
}
