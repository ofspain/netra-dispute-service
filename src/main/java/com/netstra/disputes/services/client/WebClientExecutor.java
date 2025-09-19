package com.netstra.disputes.services.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netra.commons.external.service.client.CallOperation;
import com.netra.commons.external.service.client.LogObject;
import com.netra.commons.external.service.client.LogObjectRequestEvent;
import com.netra.commons.external.service.client.TraceIdFilter;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StopWatch;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.retry.RetryBackoffSpec;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;

@Component
@Slf4j
public class WebClientExecutor {

    private static final String UNKNOWN_CALLER = "Unknown";
    private static final String SUCCESS_STATUS = "200";
    private static final int STACK_SKIP_FRAMES = 2;
    private static final int MAX_RESPONSE_LOG_SIZE = 10000; // 10KB
    private static final int MAX_REQUEST_LOG_SIZE = 5000; // 5KB
    private static final int MAX_ERROR_LOG_SIZE = 5000; // 5KB

    private final WebClient plainWebClient;
    private final WebClient oauthWebClient;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final ExecutorService timeLimiterExecutor;

    // Resilience4j components
    private final Retry retry;
    private final CircuitBreaker circuitBreaker;
    private final TimeLimiter timeLimiter;

    @Setter
    private Boolean useOAuth2 = false;

    @Autowired
    public WebClientExecutor(
            @Qualifier("webClient") WebClient plainWebClient,
            @Qualifier("oauthWebClient") WebClient oauthWebClient,
            ObjectMapper objectMapper,
            ApplicationEventPublisher eventPublisher,
            RetryRegistry retryRegistry,
            CircuitBreakerRegistry circuitBreakerRegistry,
            ExecutorService timeLimiterExecutor,
            TimeLimiter timeLimiter) {

        this.plainWebClient = plainWebClient;
        this.oauthWebClient = oauthWebClient;
        this.objectMapper = objectMapper;
        this.eventPublisher = eventPublisher;
        this.timeLimiterExecutor = timeLimiterExecutor;

        // Initialize resilience components
        this.retry = retryRegistry.retry("external-service-retry");
        this.circuitBreaker = circuitBreakerRegistry.circuitBreaker("external-service-cb");
        this.timeLimiter = timeLimiter;

        log.info("WebClientExecutor initialized with reactive resilience patterns enabled");
    }

    /**
     * Builds URI with improved efficiency and null safety
     */
    public String buildUri(String baseUrl, String path,
                           @Nullable MultiValueMap<String, String> queryParams,
                           Object... pathVariables) {

        if (!StringUtils.hasText(baseUrl) || !StringUtils.hasText(path)) {
            throw new IllegalArgumentException("BaseUrl and path cannot be null or empty");
        }

        StringBuilder urlBuilder = new StringBuilder(baseUrl);
        if (!baseUrl.endsWith("/") && !path.startsWith("/")) {
            urlBuilder.append("/");
        }

        UriComponentsBuilder builder = UriComponentsBuilder
                .fromUriString(urlBuilder.toString())
                .path(path);

        if (queryParams != null && !queryParams.isEmpty()) {
            builder.queryParams(queryParams);
        }

        return builder.buildAndExpand(pathVariables).toUriString();
    }

    /**
     * Enhanced reactive execute method with resilience patterns
     */
    public <T> T execute(HttpMethod method, String url, @Nullable Object body,
                         @Nullable MultiValueMap<String, String> headers,
                         ParameterizedTypeReference<T> responseType,
                         com.netra.commons.external.service.client.CallOperation operation, String userName) {

        validateExecuteParameters(method, url, responseType, operation, userName);

        ExecutionContext context = ExecutionContext.builder()
                .method(method)
                .url(url)
                .operation(operation)
                .userName(userName)
                .startTime(System.currentTimeMillis())
                .build();

        // Create reactive pipeline with resilience patterns
        Supplier<CompletableFuture<T>> futureSupplier = () -> {
            Mono<T> reactivePipeline = makeReactiveCall(method, url, body, headers, responseType, context)
                 //   .transform(CircuitBreaker.decorateSupplier(circuitBreaker, () -> ""))
                    .retryWhen(createReactiveRetrySpec())
                    .timeout(Duration.ofMillis(context.getTimeoutMs() > 0 ? context.getTimeoutMs() : 30000))
                    .subscribeOn(Schedulers.boundedElastic());

            return reactivePipeline.toFuture();
        };

        try {
            T result = timeLimiter.executeFutureSupplier(futureSupplier);
            log.debug("Reactive REST call completed successfully for {}", url);
            return result;

        } catch (Exception ex) {
            Throwable cause = ex instanceof CompletionException ? ex.getCause() : ex;
            log.error("Reactive REST call failed for {} after resilience controls: {}",
                    url, cause != null ? cause.getMessage() : "Unknown error", ex);

            if (cause instanceof WebClientResponseException) {
                throw (WebClientResponseException) cause;
            }

            throw new WebClientExecutionException(
                    "Reactive REST call failed with resilience controls for: " + url, cause);
        }
    }

    /**
     * Reactive execute method that returns Mono for full reactive support
     */
    public <T> Mono<T> executeAsync(HttpMethod method, String url, @Nullable Object body,
                                    @Nullable MultiValueMap<String, String> headers,
                                    ParameterizedTypeReference<T> responseType,
                                    com.netra.commons.external.service.client.CallOperation operation, String userName) {

        validateExecuteParameters(method, url, responseType, operation, userName);

        ExecutionContext context = ExecutionContext.builder()
                .method(method)
                .url(url)
                .operation(operation)
                .userName(userName)
                .startTime(System.currentTimeMillis())
                .build();

        return makeReactiveCall(method, url, body, headers, responseType, context)
                .transform(mono -> {
                    // Apply circuit breaker
                    Supplier<Mono<T>> circuitBreakerDecorated = CircuitBreaker.decorateSupplier(
                            circuitBreaker, () -> mono);
                    return circuitBreakerDecorated.get();
                })
                .retryWhen(createReactiveRetrySpec())
                .timeout(Duration.ofMillis(context.getTimeoutMs() > 0 ? context.getTimeoutMs() : 30000))
                .doOnSuccess(result -> log.debug("Reactive REST call completed successfully for {}", url))
                .doOnError(throwable -> log.error("Reactive REST call failed for {}: {}",
                        url, throwable.getMessage(), throwable))
                .subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * Core reactive REST call implementation
     */
    private <T> Mono<T> makeReactiveCall(HttpMethod method, String url, @Nullable Object body,
                                         @Nullable MultiValueMap<String, String> headers,
                                         ParameterizedTypeReference<T> responseType,
                                         ExecutionContext context) {

        StopWatch stopWatch = new StopWatch("WebClientCall");
        stopWatch.start();

        LogObject logObject = null;
        boolean loggingEnabled = log.isInfoEnabled();

        if (loggingEnabled) {
            logObject = createLogObject(method, url, body, headers,
                    context.getOperation(), context.getUserName());
        }

        WebClient client = Boolean.TRUE.equals(useOAuth2) ? oauthWebClient : plainWebClient;

        // Build reactive request
        WebClient.RequestBodySpec requestSpec = client.method(method).uri(url);

        // Add headers if present
        if (headers != null && !headers.isEmpty()) {
            requestSpec.headers(h -> h.addAll(headers));
        }

        // Add body if present
        if (body != null) {
            requestSpec.bodyValue(body);
        }

        final LogObject finalLogObject = logObject;

        return requestSpec.retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, response -> {
                    return response.bodyToMono(String.class)
                            .defaultIfEmpty("No response body")
                            .flatMap(errorBody -> {
                                WebClientResponseException ex = WebClientResponseException.create(
                                        response.statusCode().value(),
                                        response.statusCode().toString(),
                                        response.headers().asHttpHeaders(),
                                        errorBody.getBytes(),
                                        null
                                );
                                return Mono.error(ex);
                            });
                })
                .onStatus(HttpStatusCode::is5xxServerError, response -> {
                    return response.bodyToMono(String.class)
                            .defaultIfEmpty("No response body")
                            .flatMap(errorBody -> {
                                WebClientResponseException ex = WebClientResponseException.create(
                                        response.statusCode().value(),
                                        response.statusCode().toString(),
                                        response.headers().asHttpHeaders(),
                                        errorBody.getBytes(),
                                        null
                                );
                                return Mono.error(ex);
                            });
                })
                .bodyToMono(responseType)
                .doOnSuccess(response -> {
                    stopWatch.stop();
                    if (loggingEnabled && finalLogObject != null) {
                        recordSuccess(finalLogObject, response, stopWatch.getTotalTimeMillis());
                    }
                })
                .doOnError(WebClientResponseException.class, ex -> {
                    stopWatch.stop();
                    if (loggingEnabled && finalLogObject != null) {
                        handleClientError(ex, finalLogObject, stopWatch.getTotalTimeMillis());
                    }
                })
                .doOnError(throwable -> {
                    if (!(throwable instanceof WebClientResponseException)) {
                        stopWatch.stop();
                        if (loggingEnabled && finalLogObject != null) {
                            recordFailure(finalLogObject, throwable.getMessage(), stopWatch.getTotalTimeMillis());
                        }
                    }
                })
                .onErrorMap(throwable -> {
                    if (throwable instanceof WebClientResponseException) {
                        return throwable;
                    }
                    return new WebClientExecutionException("Unexpected error during reactive REST call", throwable);
                });
    }

    /**
     * Creates reactive retry specification with exponential backoff
     */
    private RetryBackoffSpec createReactiveRetrySpec() {
        return reactor.util.retry.Retry.backoff(3, Duration.ofMillis(500))
                .maxBackoff(Duration.ofSeconds(5))
                .jitter(0.5)
                .filter(throwable -> {
                    if (throwable instanceof WebClientResponseException) {
                        WebClientResponseException ex = (WebClientResponseException) throwable;
                        int status = ex.getStatusCode().value();
                        // Retry on 5xx errors, 408 (timeout), 429 (rate limit)
                        return status >= 500 || status == 408 || status == 429;
                    }

                    // Retry on connection-related exceptions
                    if (throwable instanceof WebClientRequestException) {
                        Throwable cause = throwable.getCause();
                        return cause instanceof java.net.SocketTimeoutException ||
                                cause instanceof java.net.ConnectException ||
                                cause instanceof java.nio.channels.ClosedChannelException ||
                                cause instanceof io.netty.channel.ConnectTimeoutException;
                    }

                    return false;
                })
                .doBeforeRetry(retrySignal -> {
                    log.warn("Retrying request (attempt {}) due to: {}",
                            retrySignal.totalRetries() + 1,
                            retrySignal.failure().getMessage());
                })
                .onRetryExhaustedThrow((retryBackoffSpec, retrySignal) -> {
                    log.error("Retry exhausted after {} attempts", retrySignal.totalRetries());
                    return retrySignal.failure();
                });
    }

    /**
     * Validates execute method parameters
     */
    private void validateExecuteParameters(HttpMethod method, String url,
                                           ParameterizedTypeReference<?> responseType,
                                           com.netra.commons.external.service.client.CallOperation operation, String userName) {
        if (method == null) {
            throw new IllegalArgumentException("HTTP method cannot be null");
        }
        if (!StringUtils.hasText(url)) {
            throw new IllegalArgumentException("URL cannot be null or empty");
        }
        if (responseType == null) {
            throw new IllegalArgumentException("Response type cannot be null");
        }
        if (operation == null) {
            throw new IllegalArgumentException("Store operation cannot be null");
        }
        if (useOAuth2 == null) {
            throw new IllegalStateException("OAuth2 flag must be set before REST calls");
        }
    }

    /**
     * Records successful response with size limits
     */
    private <T> void recordSuccess(LogObject logObject, T response, long timeTakenMs) {
        logObject.setIsSuccessful(true);
        logObject.setTimeTaken(formatDuration(timeTakenMs));
        logObject.setStatusCode(SUCCESS_STATUS);

        try {
            String responseStr = objectMapper.writeValueAsString(response);
            if (responseStr.length() < MAX_RESPONSE_LOG_SIZE) {
                logObject.setStoreResponse(responseStr);
            } else {
                logObject.setStoreResponse("Response too large to log (" + responseStr.length() + " chars)");
            }
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize response for logging: {}", e.getMessage());
            logObject.setStoreResponse("Serialization failed");
        }

        publishLogEventAsync(logObject);
    }

    /**
     * Handles client errors with enhanced logging
     */
    private void handleClientError(WebClientResponseException ex, LogObject logObject, long timeTakenMs) {
        logObject.setIsSuccessful(false);
        logObject.setStatusCode(String.valueOf(ex.getStatusCode().value()));
        logObject.setTimeTaken(formatDuration(timeTakenMs));

        try {
            String responseBody = ex.getResponseBodyAsString();
            if (responseBody.length() < MAX_ERROR_LOG_SIZE) {
                logObject.setStoreResponse(responseBody);
            } else {
                logObject.setStoreResponse("Error response too large to log");
            }
        } catch (Exception e) {
            logObject.setStoreResponse("Failed to read error response");
        }

        publishLogEventAsync(logObject);
    }

    /**
     * Records failures with proper error handling
     */
    private void recordFailure(LogObject logObject, String message, long timeTakenMs) {
        logObject.setIsSuccessful(false);
        logObject.setTimeTaken(formatDuration(timeTakenMs));
        logObject.setStoreResponse(StringUtils.hasText(message) ? message : "Unknown error");
        publishLogEventAsync(logObject);
    }

    /**
     * Creates log object with efficient serialization
     */
    private LogObject createLogObject(HttpMethod method, String url, Object body,
                                      MultiValueMap<String, String> headers,
                                      com.netra.commons.external.service.client.CallOperation operation, String user) {
        LogObject logObject = new LogObject();

        // Set basic properties
        logObject.setTraceID(TraceIdFilter.getTraceId());
        logObject.setUser(StringUtils.hasText(user) ? user : "anonymous");
        logObject.setStore(formatModuleName(getCallerClass()));
        logObject.setOperation(operation.name());
        logObject.setEndpoint(url);
        logObject.setHttpMethod(method.name());

        // Handle request serialization
        if (body != null) {
            try {
                String requestStr = objectMapper.writeValueAsString(body);
                if (requestStr.length() < MAX_REQUEST_LOG_SIZE) {
                    logObject.setRequest(requestStr);
                } else {
                    logObject.setRequest("Request too large to log (" + requestStr.length() + " chars)");
                }
            } catch (JsonProcessingException e) {
                log.debug("Failed to serialize request for logging: {}", e.getMessage());
                logObject.setRequest("Request serialization failed");
            }
        }

        // Handle headers
        if (headers != null && !headers.isEmpty()) {
            HttpHeaders httpHeaders = new HttpHeaders();
            httpHeaders.addAll(headers);
            logObject.setHttpHeader(httpHeaders);
        }

        return logObject;
    }

    /**
     * Async event publishing using reactive streams
     */
    private void publishLogEventAsync(LogObject logObject) {
        Mono.fromRunnable(() -> {
                    try {
                        if (log.isInfoEnabled()) {
                            log.info("Store call log: {}", objectMapper.writeValueAsString(logObject));
                        }
                        eventPublisher.publishEvent(new LogObjectRequestEvent(logObject));
                    } catch (Exception e) {
                        log.error("Failed to publish log event: {}", e.getMessage());
                    }
                })
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe();
    }

    /**
     * Caller class detection using StackWalker
     */
    private String getCallerClass() {
        return StackWalker.getInstance()
                .walk(stream -> stream
                        .skip(STACK_SKIP_FRAMES)
                        .findFirst()
                        .map(StackWalker.StackFrame::getClassName)
                        .orElse(UNKNOWN_CALLER))
                .replace("com.netra.commons.", "")
                .replace("Client", "");
    }

    /**
     * Module name formatting
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
     * Duration formatting
     */
    private String formatDuration(long millis) {
        Duration duration = Duration.ofMillis(millis);
        long seconds = duration.getSeconds();
        long ms = duration.toMillisPart();
        return String.format("%d secs, %d ms", seconds, ms);
    }

    /**
     * Execution context for better tracing
     */
    private static class ExecutionContext {
        private final HttpMethod method;
        private final String url;
        private final com.netra.commons.external.service.client.CallOperation operation;
        private final String userName;
        private final long startTime;
        private final int timeoutMs;

        private ExecutionContext(HttpMethod method, String url, com.netra.commons.external.service.client.CallOperation operation,
                                 String userName, long startTime, int timeoutMs) {
            this.method = method;
            this.url = url;
            this.operation = operation;
            this.userName = userName;
            this.startTime = startTime;
            this.timeoutMs = timeoutMs;
        }

        public static ExecutionContextBuilder builder() {
            return new ExecutionContextBuilder();
        }

        // Getters
        public HttpMethod getMethod() { return method; }
        public String getUrl() { return url; }
        public com.netra.commons.external.service.client.CallOperation getOperation() { return operation; }
        public String getUserName() { return userName; }
        public long getStartTime() { return startTime; }
        public int getTimeoutMs() { return timeoutMs; }

        public static class ExecutionContextBuilder {
            private HttpMethod method;
            private String url;
            private com.netra.commons.external.service.client.CallOperation operation;
            private String userName;
            private long startTime;
            private int timeoutMs = 30000; // Default 30 seconds

            public ExecutionContextBuilder method(HttpMethod method) {
                this.method = method;
                return this;
            }

            public ExecutionContextBuilder url(String url) {
                this.url = url;
                return this;
            }

            public ExecutionContextBuilder operation(CallOperation operation) {
                this.operation = operation;
                return this;
            }

            public ExecutionContextBuilder userName(String userName) {
                this.userName = userName;
                return this;
            }

            public ExecutionContextBuilder startTime(long startTime) {
                this.startTime = startTime;
                return this;
            }

            public ExecutionContextBuilder timeoutMs(int timeoutMs) {
                this.timeoutMs = timeoutMs;
                return this;
            }

            public ExecutionContext build() {
                return new ExecutionContext(method, url, operation, userName, startTime, timeoutMs);
            }
        }
    }

    /**
     * Custom exception for WebClient execution errors
     */
    public static class WebClientExecutionException extends RuntimeException {
        public WebClientExecutionException(String message) {
            super(message);
        }

        public WebClientExecutionException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}

/**
 * Enhanced Reactive Usage Examples:
 *
 * @Autowired
 * private WebClientExecutor webClientExecutor;
 *
 * // Blocking GET request (similar to RestClient)
 * public MyResponse getData(String id) {
 *     String url = webClientExecutor.buildUri(baseUrl, "/api/data/{id}", null, id);
 *
 *     return webClientExecutor.execute(
 *             HttpMethod.GET,
 *             url,
 *             null,
 *             null,
 *             new ParameterizedTypeReference<MyResponse>() {},
 *             CallOperation.READ,
 *             getCurrentUser()
 *     );
 * }
 *
 * // Reactive GET request (returns Mono)
 * public Mono<MyResponse> getDataAsync(String id) {
 *     String url = webClientExecutor.buildUri(baseUrl, "/api/data/{id}", null, id);
 *
 *     return webClientExecutor.executeAsync(
 *             HttpMethod.GET,
 *             url,
 *             null,
 *             null,
 *             new ParameterizedTypeReference<MyResponse>() {},
 *             CallOperation.READ,
 *             getCurrentUser()
 *     );
 * }
 *
 * // Reactive POST with chaining
 * public Mono<String> postDataReactive(MyRequest request, String userId) {
 *     MultiValueMap<String, String> queryParams = new LinkedMultiValueMap<>();
 *     queryParams.add("userId", userId);
 *
 *     MultiValueMap<String, String> headers = new LinkedMultiValueMap<>();
 *     headers.add("Content-Type", "application/json");
 *
 *     String url = webClientExecutor.buildUri(baseUrl, "/api/submit", queryParams);
 *
 *     return webClientExecutor.executeAsync(
 *             HttpMethod.POST,
 *             url,
 *             request,
 *             headers,
 *             new ParameterizedTypeReference<String>() {},
 *             CallOperation.CREATE,
 *             getCurrentUser()
 *     )
 *     .flatMap(response -> processResponse(response))
 *     .onErrorResume(this::handleError);
 * }
 */
