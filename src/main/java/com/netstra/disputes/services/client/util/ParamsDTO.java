package com.netstra.disputes.services.client.util;

import com.netra.commons.models.endpoint.DynamicHeader;

import java.util.Map;


public record ParamsDTO(Map<String, String> pathParams,Map<String, String> queryParams,Map<DynamicHeader, String> dynamicHeaderValues) {
}
