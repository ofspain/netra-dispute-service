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

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.random.RandomGenerator;

@UtilityClass
public class Utility {

    public ResolvedRequest prepareEndpointRequest(EndpointDetail detail, EndpointConfig config,
                                                  Map<String,String> requestBodyContext, ParamsDTO paramsDTO, VaultManager manager){
        Map<String, String> pathParams = paramsDTO.pathParams();
        Map<String, String> queryParams = paramsDTO.queryParams();
        Map<DynamicHeader, String> dynamicHeaderValues = paramsDTO.dynamicHeaderValues();

       // EndpointDetail detail = isMultiple ? config.getMultipleTransaction() : config.getUniqueTransaction();

        String resolvedUrl = UriBuilderUtil.resolveUrl(config.getNetwork().getBaseUrl(), detail.getUrl(), pathParams, queryParams);
        HttpMethod method = convertMethod(detail.getMethod());


        HttpHeaders headers = buildHeaders(detail.getHeaders(), dynamicHeaderValues, manager);

        HttpEntity<?> entity;
        boolean hasBody = BasicUtil.validString(detail.getRequestBodyTemplate());

        if (hasBody) {
            String body = UriBuilderUtil.resolveRequestBody(detail.getRequestBodyTemplate(), requestBodyContext);
            headers.setContentType(MediaType.APPLICATION_JSON);
            entity = new HttpEntity<>(body, headers);
        } else {
            entity = new HttpEntity<>(headers);
        }

        return new ResolvedRequest(resolvedUrl, method, entity);
    }

    private HttpMethod convertMethod(EndpointDetail.HTTPMethod method) {
        return method == EndpointDetail.HTTPMethod.POST ? HttpMethod.POST : HttpMethod.GET;
    }
    public HttpHeaders buildHeaders(List<StaticHeader> staticHeaders, Map<DynamicHeader, String> dynamicHeaderValues, VaultManager vaultManager) {
        HttpHeaders headers = new HttpHeaders();

        // Process static headers
        if (staticHeaders != null) {
            for (StaticHeader sh : staticHeaders) {
                String value = sh.getValue();
                if (sh.isSecret()) {
                    //todo: Resolve vault secret placeholders, e.g., ${vault:vk-123}
                    value = vaultManager.getSecret(value); // Assume a utility to fetch secret from vault
                }
                headers.add(sh.getName(), value);
            }
        }

        // Process dynamic headers
        if (dynamicHeaderValues != null) {
            for (Map.Entry<DynamicHeader, String> entry : dynamicHeaderValues.entrySet()) {
                DynamicHeader dynHeader = entry.getKey();
                String value = entry.getValue();

                if (dynHeader.isRequired() && value == null) {
                    //todo: change to app level exception
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

    public static String calculateCachedRestClientId(EndpointConfigIdentity identity){
        Long ownerId = identity.ownerId();
        String domainCode = identity.domainCode();
        DomainType type = identity.type();

        String uuid = ownerId+":"+":"+domainCode + ":"+type.name();

        return uuid;
    }
}
