package com.netstra.disputes.services.client.util;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;

public record ResolvedRequest(String resolvedUrl, HttpMethod method, HttpEntity<?> entity){
}


