package com.netstra.disputes.services.client.util;

import com.netra.commons.models.endpoint.*;
import com.netra.commons.util.BasicUtil;
import com.netra.commons.util.UriBuilderUtil;
import lombok.experimental.UtilityClass;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;

import java.util.Map;

@UtilityClass
public class Utility {

    public ResolvedRequest prepareEndpointRequest(EndpointDetail detail, EndpointConfig config, Boolean isMultiple, Map<String,String> requestBodyContext, ParamsDTO paramsDTO){
        Map<String, String> pathParams = paramsDTO.pathParams();
        Map<String, String> queryParams = paramsDTO.queryParams();
        Map<String, String> dynamicHeaderValues = paramsDTO.dynamicHeaderValues();

       // EndpointDetail detail = isMultiple ? config.getMultipleTransaction() : config.getUniqueTransaction();

        String resolvedUrl = UriBuilderUtil.resolveUrl(config.getBaseUrl(), detail.getUrl(), pathParams, queryParams);
        HttpMethod method = convertMethod(detail.getMethod());
        HttpHeaders headers = UriBuilderUtil.buildHeaders(detail.getHeaders(), dynamicHeaderValues);

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

    public HttpMethod convertMethod(HTTPMethod m) {
        return m == HTTPMethod.POST ? HttpMethod.POST : HttpMethod.GET;
    }
}
