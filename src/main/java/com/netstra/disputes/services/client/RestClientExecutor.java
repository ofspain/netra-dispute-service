package com.netstra.disputes.services.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netra.commons.models.endpoint.DynamicHeader;
import com.netra.commons.models.endpoint.EndpointConfig;
import com.netra.commons.trace.CallOperation;
import com.netra.commons.trace.LogObject;
import com.netra.commons.trace.LogObjectRequestEvent;
import com.netra.commons.trace.TraceIdFilter;
import com.netstra.disputes.services.client.util.*;
import com.netstra.disputes.services.client.vault.VaultManager;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.timelimiter.TimeLimiter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StopWatch;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Supplier;


@Component
@Slf4j
public class RestClientExecutor {

    private static final String UNKNOWN_CALLER = "Unknown";
    private static final String SUCCESS_STATUS = "200";
    private static final int STACK_SKIP_FRAMES = 2;

    private final RestClient plainRestClient;
    private final RestClient oauthRestClient;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final ExecutorService timeLimiterExecutor;

    // Resilience4j components
    private final Retry retry;
    private final CircuitBreaker circuitBreaker;
    private final TimeLimiter timeLimiter;

    @Setter
    private Boolean useOAuth2 = false;

    @Qualifier("timeLimiterScheduler")
    private final ScheduledExecutorService timeLimiterScheduler;

    private final VaultManager vaultManager;
    private final ExecutorUtil executor;


    @Autowired
    public RestClientExecutor(
            @Qualifier("restClient") RestClient plainRestClient,
            @Qualifier("oauthRestClient") RestClient oauthRestClient,
            ObjectMapper objectMapper,
            ApplicationEventPublisher eventPublisher,
            RetryRegistry retryRegistry,
            CircuitBreakerRegistry circuitBreakerRegistry,
            ExecutorService timeLimiterExecutor,
            TimeLimiter timeLimiter,
            @Qualifier("timeLimiterScheduler") ScheduledExecutorService timeLimiterScheduler,
            @Qualifier("awsVault")VaultManager vaultManager,
            ExecutorUtil executor) {

        this.plainRestClient = plainRestClient;
        this.oauthRestClient = oauthRestClient;
        this.objectMapper = objectMapper;
        this.eventPublisher = eventPublisher;
        this.timeLimiterExecutor = timeLimiterExecutor;

        // Initialize resilience components with better naming
        this.retry = retryRegistry.retry("external-service-retry");
        this.circuitBreaker = circuitBreakerRegistry.circuitBreaker("external-service-cb");
        this.timeLimiter = timeLimiter;
        this.timeLimiterScheduler = timeLimiterScheduler;

        this.vaultManager = vaultManager;
        this.executor = executor;

        log.info("RestClientExecutor initialized with resilience patterns enabled");
    }


    public <T> T executeUniqueTransactionRequest(
            RestClient client,
            EndpointConfig config,
            Map<String, String> pathParams,
            Map<String, String> queryParams,
            Map<DynamicHeader, String> dynamicHeaderValues,
            Map<String, String> requestBodyContext,
            ParameterizedTypeReference<T> responseType
    ) {
        // 1️⃣ Prepare ParamsDTO
        ParamsDTO paramsDTO = new ParamsDTO(pathParams, queryParams, dynamicHeaderValues);

        // 2️⃣ Prepare the resolved endpoint request
        ResolvedRequest dto = Utility.prepareEndpointRequest(
                config.getEndpoints().get(EndpointConfig.OperationType.UNIQUE_TRANSACTION_SEARCH),
                config,
                requestBodyContext,
                paramsDTO,
                vaultManager
        );

        // 3️⃣ Log the CURL equivalent (optional)
        String curl = toCurl(dto.method(), dto.resolvedUrl(), dto.entity());
        log.info("CURL URL {}", curl);

        // 4️⃣ Delegate execution to the common executor
        // `audit=false` because unique transaction does not require audit/timing
        return executor.executeRequest(
                client,
                dto,
                responseType,
                config.getDomainCode(),
                false
        );
    }


    public <T> T executeMultipleTransactionRequest(
            RestClient client,
            EndpointConfig config,
            Map<String, String> pathParams,
            Map<String, String> queryParams,
            Map<DynamicHeader, String> dynamicHeaderValues,
            Map<String, String> requestBodyContext,
            ParameterizedTypeReference<T> responseType
    ) {
        // 1️⃣ Prepare ParamsDTO
        ParamsDTO paramsDTO = new ParamsDTO(pathParams, queryParams, dynamicHeaderValues);

        // 2️⃣ Prepare the resolved endpoint request
        ResolvedRequest dto = Utility.prepareEndpointRequest(
                config.getEndpoints().get(EndpointConfig.OperationType.BULK_TRANSACTION_SEARCH),
                config,
                requestBodyContext,
                paramsDTO,
                vaultManager
        );

        // 3️⃣ Log the CURL equivalent (optional)
        String curl = toCurl(dto.method(), dto.resolvedUrl(), dto.entity());
        log.info("CURL URL {}", curl);

        // 4️⃣ Delegate execution to the common executor
        // `audit=false` because unique transaction does not require audit/timing
        return executor.executeRequest(
                client,
                dto,
                responseType,
                config.getDomainCode(),
                false
        );
    }


    /**
     * Enhanced execute method with better error handling and performance
     */
    public <T> T executeWithResilience(
            RestClient client,
            HttpMethod method,
            String url,
            @Nullable Object body,
            @Nullable HttpHeaders headers,
            ParameterizedTypeReference<T> responseType,
            boolean audit,
            String username,
            CallOperation operation
    ) {
        // Build a ResolvedRequest-like object
        ResolvedRequest dto = new ResolvedRequest(url, method, new HttpEntity<>(body, headers));

        // Wrap the call into a Supplier<T> for resilience
        Supplier<T> syncSupplier = () -> executeRequest(client, dto, responseType, audit, operation, username);

        Supplier<T> retryDecorated = Retry.decorateSupplier(retry, syncSupplier);
        Supplier<T> circuitBreakerDecorated = CircuitBreaker.decorateSupplier(circuitBreaker, retryDecorated);

        try {
            return timeLimiter.executeCompletionStage(
                    (ScheduledExecutorService) timeLimiterExecutor,
                    () -> CompletableFuture.supplyAsync(circuitBreakerDecorated, timeLimiterExecutor)
            ).toCompletableFuture().join();
        } catch (CompletionException ce) {
            Throwable cause = ce.getCause();
            if (cause instanceof RestClientResponseException) throw (RestClientResponseException) cause;
            throw new RestClientExecutionException("REST call failed for: " + url, cause);
        }
    }

    private <T> T executeRequest(
            RestClient client,
            ResolvedRequest dto,
            ParameterizedTypeReference<T> responseType,
            boolean audit,
            CallOperation operation,
            String userName
    ) {
        HttpMethod method = dto.method();
        String url = dto.resolvedUrl();
        HttpEntity<?> entity = dto.entity();
        HttpHeaders headers = entity.getHeaders();
        Object body = entity.getBody();

        // Build execution context with builder
        ExecutionContext context = ExecutionContext.builder()
                .method(method)
                .url(url)
                .operation(operation)
                .userName(userName)
                .startTime(System.currentTimeMillis())
                .build();

        // Delegate actual HTTP call to makeCall
        T response = makeCall(client, method, url, body, headers, responseType, context);

        // Optionally handle audit / metrics
        if (audit) {
            // TODO: implement audit logic here
            // auditLogger.logSuccess(domainCode, method, url, headers, body, response, context.elapsed());
        }

        return response;
    }




    /**
     * Streamlined REST call execution with better performance
     */
    private <T> T makeCall(RestClient client, HttpMethod method,
                           String url, @Nullable Object body,
                           @Nullable MultiValueMap<String, String> headers,
                           ParameterizedTypeReference<T> responseType,
                           ExecutionContext context) {

        StopWatch stopWatch = new StopWatch("RestCall");
        stopWatch.start();

        LogObject logObject = null;
        boolean loggingEnabled = log.isInfoEnabled();

        if (loggingEnabled) {
            logObject = createLogObject(method, url, body, headers,
                    context.getOperation(), context.getUserName());
        }

        try {
           // RestClient client = Boolean.TRUE.equals(useOAuth2) ? oauthRestClient : plainRestClient;

            // Build request more efficiently
            RestClient.RequestBodySpec request = client.method(method).uri(url);

            // Add headers if present
            if (headers != null && !headers.isEmpty()) {
                request.headers(h -> h.addAll(headers));
            }

            // Add body if present
            if (body != null) {
                request.body(body);
            }

            // Execute with enhanced error handling
            T response = request.retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                        throw new RestClientResponseException(
                                "Client error: " + res.getStatusCode(),
                                res.getStatusCode(),
                                res.getStatusText(),
                                res.getHeaders(),
                                null, // We'll read body separately if needed
                                null
                        );
                    })
                    .onStatus(HttpStatusCode::is5xxServerError, (req, res) -> {
                        throw new RestClientResponseException(
                                "Server error: " + res.getStatusCode(),
                                res.getStatusCode(),
                                res.getStatusText(),
                                res.getHeaders(),
                                null,
                                null
                        );
                    })
                    .body(responseType);

            stopWatch.stop();

            if (loggingEnabled && logObject != null) {
                recordSuccess(logObject, response, stopWatch.getTotalTimeMillis());
            }

            return response;

        } catch (RestClientResponseException ex) {
            stopWatch.stop();
            if (loggingEnabled && logObject != null) {
                handleClientError(ex, logObject, stopWatch.getTotalTimeMillis());
            }
            throw ex;

        } catch (Exception ex) {
            stopWatch.stop();
            if (loggingEnabled && logObject != null) {
                recordFailure(logObject, ex.getMessage(), stopWatch.getTotalTimeMillis());
            }
            throw new RestClientExecutionException("Unexpected error during REST call", ex);
        }
    }

    /**
     * More efficient success recording
     */
    private <T> void recordSuccess(LogObject logObject, T response, long timeTakenMs) {
        logObject.setIsSuccessful(true);
        logObject.setTimeTaken(formatDuration(timeTakenMs));
        logObject.setStatusCode(SUCCESS_STATUS);

        // Only serialize response if it's reasonable size (avoid memory issues)
        try {
            String responseStr = objectMapper.writeValueAsString(response);
            if (responseStr.length() < 10000) { // 10KB limit
                logObject.setStoreResponse(responseStr);
            } else {
                logObject.setStoreResponse("Response too large to log (" + responseStr.length() + " chars)");
            }
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize response for logging: {}", e.getMessage());
            logObject.setStoreResponse("Serialization failed");
        }

        publishLogEvent(logObject);
    }

    /**
     * Enhanced client error handling
     */
    private void handleClientError(RestClientResponseException ex, LogObject logObject, long timeTakenMs) {
        logObject.setIsSuccessful(false);
        logObject.setStatusCode(String.valueOf(ex.getStatusCode().value()));
        logObject.setTimeTaken(formatDuration(timeTakenMs));

        // Safely handle response body
        try {
            String responseBody = ex.getResponseBodyAsString();
            if (responseBody.length() < 5000) { // 5KB limit for error responses
                logObject.setStoreResponse(responseBody);
            } else {
                logObject.setStoreResponse("Error response too large to log");
            }
        } catch (Exception e) {
            logObject.setStoreResponse("Failed to read error response");
        }

        publishLogEvent(logObject);
    }

    /**
     * Improved failure recording
     */
    private void recordFailure(LogObject logObject, String message, long timeTakenMs) {
        logObject.setIsSuccessful(false);
        logObject.setTimeTaken(formatDuration(timeTakenMs));
        logObject.setStoreResponse(StringUtils.hasText(message) ? message : "Unknown error");
        publishLogEvent(logObject);
    }

    /**
     * More efficient log object creation
     */
    private LogObject createLogObject(HttpMethod method, String url, Object body,
                                      MultiValueMap<String, String> headers,
                                      CallOperation operation, String user) {
        LogObject logObject = new LogObject();

        // Set basic properties
        logObject.setTraceID(TraceIdFilter.getTraceId());
        logObject.setUser(StringUtils.hasText(user) ? user : "anonymous");
        logObject.setStore(formatModuleName(getCallerClass()));
        logObject.setOperation(operation.name());
        logObject.setEndpoint(url);
        logObject.setHttpMethod(method.name());

        // Handle request serialization more efficiently
        if (body != null) {
            try {
                String requestStr = objectMapper.writeValueAsString(body);
                if (requestStr.length() < 5000) { // 5KB limit
                    logObject.setRequest(requestStr);
                } else {
                    logObject.setRequest("Request too large to log (" + requestStr.length() + " chars)");
                }
            } catch (JsonProcessingException e) {
                log.debug("Failed to serialize request for logging: {}", e.getMessage());
                logObject.setRequest("Request serialization failed");
            }
        }

        // Handle headers more efficiently
        if (headers != null && !headers.isEmpty()) {
            HttpHeaders httpHeaders = new HttpHeaders();
            httpHeaders.addAll(headers);
            logObject.setHttpHeader(httpHeaders);
        }

        return logObject;
    }

    /**
     * Async event publishing to avoid blocking
     */
    private void publishLogEvent(LogObject logObject) {
        CompletableFuture.runAsync(() -> {
            try {
                if (log.isInfoEnabled()) {
                    log.info("Store call log: {}", objectMapper.writeValueAsString(logObject));
                }
                eventPublisher.publishEvent(new LogObjectRequestEvent(logObject));
            } catch (Exception e) {
                log.error("Failed to publish log event: {}", e.getMessage());
            }
        }, timeLimiterExecutor);
    }

    /**
     * More efficient caller class detection with caching potential
     */
    private String getCallerClass() {
        return StackWalker.getInstance()
                .walk(stream -> stream
                        .skip(STACK_SKIP_FRAMES)
                        .findFirst()
                        .map(StackWalker.StackFrame::getClassName)
                        .orElse(UNKNOWN_CALLER))
                .replace("com.interswitch.backbone.arbitertransactionstoremanager.", "")
                .replace("Client", "");
    }

    /**
     * Improved module name formatting
     */
    public static String formatModuleName(String input) {
        if (!StringUtils.hasText(input) || !input.contains(".")) {
            return StringUtils.hasText(input) ? input : UNKNOWN_CALLER;
        }

        String module = input.substring(input.lastIndexOf('.') + 1);
        return (module.length() <= 3 && !module.equals(module.toUpperCase()))
                ? module.toUpperCase()
                : module;
    }

    /**
     * Better duration formatting using Duration class
     */
    private String formatDuration(long millis) {
        Duration duration = Duration.ofMillis(millis);
        long seconds = duration.getSeconds();
        long ms = duration.toMillisPart();
        return String.format("%d secs, %d ms", seconds, ms);
    }

    public static String toCurl(HttpMethod method, String url, HttpEntity<?> entity) {
        StringBuilder curl = new StringBuilder("curl -X ").append(method.name());

        // Add headers
        HttpHeaders headers = entity.getHeaders();
        if (headers != null && !headers.isEmpty()) {
            headers.forEach((key, values) -> {
                for (String value : values) {
                    curl.append(" -H '").append(key).append(": ").append(value).append("'");
                }
            });
        }

        // Add body if present
        Object body = entity.getBody();
        if (body != null) {
            String bodyContent;

            try {
                // Try pretty-print JSON if body is not already a String
                if (!(body instanceof String)) {
                    ObjectMapper mapper = new ObjectMapper();
                    bodyContent = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(body);
                } else {
                    bodyContent = (String) body;
                }
            } catch (Exception e) {
                bodyContent = body.toString(); // Fallback
            }

            // Escape single quotes for shell safety
            String escapedBody = bodyContent.replace("'", "'\"'\"'");
            curl.append(" -d '").append(escapedBody).append("'");
        }

        // Add URL last
        curl.append(" '").append(url).append("'");

        return curl.toString();
    }




    /**
     * Custom exception for better error handling
     */
    public static class RestClientExecutionException extends RuntimeException {
        public RestClientExecutionException(String message) {
            super(message);
        }

        public RestClientExecutionException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}

/**
 * Enhanced Usage Examples:
 *
 * @Autowired
 * private RestClientExecutor restClientExecutor;
 *
 * // GET request
 * public MyResponse getData(String id) {
 *     String url = restClientExecutor.buildUri(baseUrl, "/api/data/{id}", null, id);
 *
 *     return restClientExecutor.execute(
 *             HttpMethod.GET,
 *             url,
 *             null, // no body
 *             null, // no headers
 *             new ParameterizedTypeReference<MyResponse>() {},
 *             StoreOperation.READ,
 *             getCurrentUser()
 *     );
 * }
 *
 * // POST with query params and headers
 * public ResponseEntity<String> postData(MyRequest request, String userId) {
 *     MultiValueMap<String, String> queryParams = new LinkedMultiValueMap<>();
 *     queryParams.add("userId", userId);
 *
 *     MultiValueMap<String, String> headers = new LinkedMultiValueMap<>();
 *     headers.add("Content-Type", "application/json");
 *     headers.add("X-Custom-Header", "value");
 *
 *     String url = restClientExecutor.buildUri(baseUrl, "/api/submit", queryParams);
 *
 *     return restClientExecutor.execute(
 *             HttpMethod.POST,
 *             url,
 *             request,
 *             headers,
 *             new ParameterizedTypeReference<ResponseEntity<String>>() {},
 *             StoreOperation.CREATE,
 *             getCurrentUser()
 *     );
 * }
 */