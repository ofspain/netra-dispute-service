package com.netstra.disputes.services.client.util;

import com.netra.commons.trace.AuditLogger;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.core.ParameterizedTypeReference;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.web.client.RestClient;

@Component
public class ExecutorUtil {

    private final MeterRegistry meterRegistry;
    private final AuditLogger auditLogger;

    public ExecutorUtil(MeterRegistry meterRegistry, AuditLogger auditLogger) {
        this.meterRegistry = meterRegistry;
        this.auditLogger = auditLogger;
    }

    public <T> T executeRequest(
            RestClient client,
            ResolvedRequest dto,
            ParameterizedTypeReference<T> responseType,
            String domainCode,
            boolean audit
    ) {
        HttpMethod method = dto.method();
        String resolvedUrl = dto.resolvedUrl();
        HttpEntity<?> entity = dto.entity();

        HttpHeaders headers = entity.getHeaders();
        Object body = entity.getBody();
        boolean hasBody = body != null;

        String bodyContent = hasBody ? body.toString() : null;

        RestClient.RequestBodySpec request = client
                .method(method)
                .uri(resolvedUrl)
                .headers(h -> h.addAll(headers));

        if (!audit) {
            // Simple execution without audit/timer
            if (hasBody) {
                return request.body(body).retrieve().body(responseType);
            } else {
                return request.retrieve().body(responseType);
            }
        }

        // Execution with audit and timing
        Timer.Sample sample = Timer.start(meterRegistry);
        long start = System.currentTimeMillis();
        try {
            RestClient.ResponseSpec responseSpec = hasBody
                    ? request.body(bodyContent).retrieve()
                    : request.retrieve();

            T result = responseSpec.body(responseType);

            auditLogger.logSuccess(domainCode, method, resolvedUrl, headers, bodyContent, result, System.currentTimeMillis() - start);
            sample.stop(meterRegistry.timer("external_call", "domain", domainCode, "status", "success"));
            return result;
        } catch (Exception e) {
            auditLogger.logFailure(domainCode, method, resolvedUrl, headers, bodyContent, e, System.currentTimeMillis() - start);
            sample.stop(meterRegistry.timer("external_call", "domain", domainCode, "status", "failure"));
            throw e;
        }
    }
}

