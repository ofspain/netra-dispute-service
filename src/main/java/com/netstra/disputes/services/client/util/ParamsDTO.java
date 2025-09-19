package com.netstra.disputes.services.client.util;

import java.util.Map;


public record ParamsDTO(Map<String, String> pathParams,Map<String, String> queryParams,Map<String, String> dynamicHeaderValues) {
}
