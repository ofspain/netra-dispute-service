package com.netstra.disputes.services.client;

import com.netra.commons.models.EndpointConfig;
import com.netra.commons.models.EndpointConfig.EndpointDetail;
import com.netra.commons.models.EndpointConfig.EndpointHeader;
import com.netra.commons.models.EndpointConfig.HTTPMethod;
import org.springframework.http.HttpMethod;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EndpointRequestBuilder {

    public record ResolvedEndpointRequest(
            String url,
            HttpMethod method,
            Map<String, String> headers
    ) {}

    /**
     * Builds the complete request from config + runtime params.
     *
     * @param config           The endpoint configuration model
     * @param forMultiple      Whether to use the multipleTransaction endpoint
     * @param runtimeParams    A map of path/query/header parameter values
     * @return A resolved endpoint request with URL, method, and headers
     */
    public static ResolvedEndpointRequest build(
            EndpointConfig config,
            boolean forMultiple,
            Map<String, String> runtimeParams
    ) {
        EndpointDetail detail = forMultiple ? config.getMultipleTransaction() : config.getUniqueTransaction();
        if (detail == null) {
            throw new IllegalArgumentException("Endpoint detail is missing.");
        }

        // Resolve method
        HttpMethod method = detail.getMethod() == HTTPMethod.GET ? HttpMethod.GET : HttpMethod.POST;

        // Build base URL
        String resolvedUrl = buildResolvedUrl(config.getBaseUrl(), detail, runtimeParams);

        // Build headers (static + dynamic)
        Map<String, String> headers = resolveHeaders(detail.getHeaders(), runtimeParams);

        return new ResolvedEndpointRequest(resolvedUrl, method, headers);
    }

    private static String buildResolvedUrl(
            String baseUrl,
            EndpointDetail detail,
            Map<String, String> runtimeParams
    ) {
        String fullUrl = baseUrl + detail.getUrl();

        // Replace path variables
        if (detail.getPathParamKeys() != null) {
            for (String key : detail.getPathParamKeys()) {
                String value = runtimeParams.get(key);
                if (value == null) {
                    throw new IllegalArgumentException("Missing path param: " + key);
                }
                fullUrl = fullUrl.replace("{" + key + "}", value);
            }
        }

        // Add query params
        UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromHttpUrl(fullUrl);
        if (detail.getQueryParamKeys() != null) {
            for (String key : detail.getQueryParamKeys()) {
                String value = runtimeParams.get(key);
                if (value == null) {
                    throw new IllegalArgumentException("Missing query param: " + key);
                }
                uriBuilder.queryParam(key, value);
            }
        }

        return uriBuilder.toUriString();
    }

    public static String resolveRequestBody(String template, Map<String, String> context) {
        if (template == null) return null;
        for (Map.Entry<String, String> entry : context.entrySet()) {
            String placeholder = "${" + entry.getKey() + "}";
            template = template.replace(placeholder, entry.getValue());
        }
        return template;
    }

    private static Map<String, String> resolveHeaders(
            List<EndpointHeader> headers,
            Map<String, String> runtimeParams
    ) {
        Map<String, String> resolved = new HashMap<>();

        if (headers == null) return resolved;

        for (EndpointHeader header : headers) {
            String name = header.getName();
            if (name == null || name.isBlank()) continue;

            if (header.isDynamic()) {
                String value = runtimeParams.get(name);
                if (value == null) {
                    throw new IllegalArgumentException("Missing dynamic header value for: " + name);
                }
                resolved.put(name, value);
            } else {
                resolved.put(name, header.getValue());
            }
        }

        return resolved;
    }
}


