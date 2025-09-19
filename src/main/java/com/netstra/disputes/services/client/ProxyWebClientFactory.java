package com.netstra.disputes.services.client;

import com.netra.commons.models.EndpointConfig;
import com.netra.commons.util.BasicUtil;
import com.netra.commons.util.SecretCryptoUtil;
import io.netty.channel.ChannelOption;
import io.netty.channel.epoll.EpollChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.*;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;
import reactor.netty.transport.ProxyProvider;
import reactor.util.retry.Retry;
import reactor.util.retry.RetryBackoffSpec;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProxyWebClientFactory {

    private final SecretCryptoUtil cryptoUtil;

    // Default configuration constants
    private static final int DEFAULT_MAX_CONNECTIONS = 500;
    private static final int DEFAULT_PENDING_ACQUIRE_TIMEOUT = 45;
    private static final int DEFAULT_MAX_IDLE_TIME = 30;
    private static final int DEFAULT_MAX_LIFE_TIME = 60;
    private static final int DEFAULT_EVICT_IN_BACKGROUND = 120;
    private static final int MAX_IN_MEMORY_SIZE = 16 * 1024 * 1024; // 16MB
    private static final int DEFAULT_RETRY_ATTEMPTS = 3;
    private final Validator validator;

    /**
     * Builds a WebClient configured with proxy settings from the provided EndpointConfig.
     *
     * @param config EndpointConfig containing proxy configuration
     * @return WebClient configured with proxy settings and resilience features
     * @throws IllegalArgumentException if proxy is not enabled or configuration is invalid
     */
    public WebClient buildFor(EndpointConfig config) {

        Set<ConstraintViolation<EndpointConfig>> violations = validator.validate(config);
        if (!violations.isEmpty()) {
            String errorMessages = violations.stream()
                    .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                    .collect(Collectors.joining(", "));
            throw new IllegalArgumentException("Invalid config: " + errorMessages);
        }

        EndpointConfig.ProxyConfig proxyConfig = config.getProxy();

        String host = proxyConfig.getHost();
        int port = proxyConfig.getPort();
        String username = proxyConfig.getUsername();
        String encryptedPassword = proxyConfig.getPassword();
        int timeoutMs = config.getTimeoutMillis();
        boolean useProxy = config.isUseProxy();

        log.info("Building WebClient with proxy: {}:{} (timeout: {}ms)", host, port, timeoutMs);

        String decryptedPassword = null;
        if (useProxy && (BasicUtil.validString(encryptedPassword))) {
            try {
                decryptedPassword = cryptoUtil.decrypt(encryptedPassword);
            } catch (Exception e) {
                log.error("Failed to decrypt proxy password", e);
                throw new IllegalStateException("Failed to decrypt proxy password", e);
            }
        }

        return createWebClientWithProxy(host, port, username, decryptedPassword, timeoutMs);
    }

    /**
     * Creates a WebClient with comprehensive proxy configuration and resilience features.
     */
    private WebClient createWebClientWithProxy(String proxyHost, int proxyPort,
                                               String proxyUsername, String proxyPassword,
                                               int timeoutMs) {

        // Create optimized connection provider
        ConnectionProvider connectionProvider = createConnectionProvider();

        // Configure HTTP client with proxy
        HttpClient httpClient = createHttpClientWithProxy(
                proxyHost, proxyPort, proxyUsername, proxyPassword, timeoutMs, connectionProvider);

        // Create client connector
        ReactorClientHttpConnector connector = new ReactorClientHttpConnector(httpClient);

        // Configure exchange strategies for larger payloads
        ExchangeStrategies strategies = ExchangeStrategies.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(MAX_IN_MEMORY_SIZE))
                .build();

        return WebClient.builder()
                .clientConnector(connector)
                .exchangeStrategies(strategies)
                .filter(loggingFilter())
                .filter(retryFilter())
                .filter(errorHandlingFilter())
                .filter(timeoutFilter(Duration.ofMillis(timeoutMs)))
                .build();
    }

    /**
     * Creates optimized connection provider for better connection management.
     */
    private ConnectionProvider createConnectionProvider() {
        return ConnectionProvider.builder("proxy-connection-pool")
                .maxConnections(DEFAULT_MAX_CONNECTIONS)
                .maxIdleTime(Duration.ofSeconds(DEFAULT_MAX_IDLE_TIME))
                .maxLifeTime(Duration.ofSeconds(DEFAULT_MAX_LIFE_TIME))
                .pendingAcquireTimeout(Duration.ofSeconds(DEFAULT_PENDING_ACQUIRE_TIMEOUT))
                .evictInBackground(Duration.ofSeconds(DEFAULT_EVICT_IN_BACKGROUND))
                .build();
    }

    /**
     * Creates HttpClient with comprehensive proxy configuration.
     */
    private HttpClient createHttpClientWithProxy(String proxyHost, int proxyPort,
                                                 String proxyUsername, String proxyPassword,
                                                 int timeoutMs, ConnectionProvider connectionProvider) {

        HttpClient httpClient = HttpClient.create(connectionProvider)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, timeoutMs)
                .option(ChannelOption.SO_KEEPALIVE, true)
                .option(ChannelOption.TCP_NODELAY, true);

        // Configure TCP keep-alive if supported
        try {
            httpClient = httpClient.option(EpollChannelOption.TCP_KEEPIDLE, 600)
                    .option(EpollChannelOption.TCP_KEEPINTVL, 60)
                    .option(EpollChannelOption.TCP_KEEPCNT, 8);
        } catch (Exception e) {
            log.debug("TCP keep-alive options not available on this platform: {}", e.getMessage());
        }

        // Configure read/write timeouts
        httpClient = httpClient.doOnConnected(conn -> {
            conn.addHandlerLast(new ReadTimeoutHandler(timeoutMs, TimeUnit.MILLISECONDS));
            conn.addHandlerLast(new WriteTimeoutHandler(timeoutMs, TimeUnit.MILLISECONDS));
        });

        // Configure proxy
        if (proxyUsername != null && !proxyUsername.isBlank() && proxyPassword != null) {
            log.debug("Configuring authenticated proxy: {}:{}@{}:{}", proxyUsername, "***", proxyHost, proxyPort);

            httpClient = httpClient.proxy(proxy -> proxy
                    .type(ProxyProvider.Proxy.HTTP)
                    .host(proxyHost)
                    .port(proxyPort)
                    .username(proxyUsername)
                    .password(s -> proxyPassword)
                    .connectTimeoutMillis(timeoutMs)
            );
        } else {
            log.debug("Configuring proxy without authentication: {}:{}", proxyHost, proxyPort);

            httpClient = httpClient.proxy(proxy -> proxy
                    .type(ProxyProvider.Proxy.HTTP)
                    .host(proxyHost)
                    .port(proxyPort)
                    .connectTimeoutMillis(timeoutMs)
            );
        }

        // Configure response timeout
        httpClient = httpClient.responseTimeout(Duration.ofMillis(timeoutMs));

        // Enable HTTP/2 support with fallback to HTTP/1.1
        httpClient = httpClient.protocol(
                reactor.netty.http.HttpProtocol.H2C,
                reactor.netty.http.HttpProtocol.HTTP11
        );

        // Configure SSL if needed
//        httpClient = httpClient.secure(sslContextSpec ->
//                sslContextSpec.handshakeTimeout(Duration.ofMillis(timeoutMs))
//        );

        return httpClient;
    }

    /**
     * Comprehensive logging filter for request/response tracking.
     */
    private ExchangeFilterFunction loggingFilter() {
        return ExchangeFilterFunction.ofRequestProcessor(request -> {
            if (log.isDebugEnabled()) {
                log.debug("WebClient Request: {} {} - Headers: {}",
                        request.method(), request.url(), request.headers());
            }
            return Mono.just(request);
        }).andThen(ExchangeFilterFunction.ofResponseProcessor(response -> {
            if (log.isDebugEnabled()) {
                log.debug("WebClient Response: {} - Headers: {}",
                        response.statusCode(), response.headers().asHttpHeaders());
            }
            return Mono.just(response);
        }));
    }

    /**
     * Intelligent retry filter with exponential backoff.
     */
    private ExchangeFilterFunction retryFilter() {
        return (request, next) -> {
            return next.exchange(request)
                    .retryWhen(createRetrySpec())
                    .doOnError(throwable -> {
                        log.error("Request failed after retries: {} {}",
                                request.method(), request.url(), throwable);
                    });
        };
    }

    /**
     * Creates intelligent retry specification.
     */
    private RetryBackoffSpec createRetrySpec() {
        return Retry.backoff(DEFAULT_RETRY_ATTEMPTS, Duration.ofMillis(500))
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
                        // Retry on timeouts, connection refused, and network issues
                        return cause instanceof java.net.SocketTimeoutException ||
                                cause instanceof java.net.ConnectException ||
                                cause instanceof java.nio.channels.ClosedChannelException ||
                                cause instanceof io.netty.channel.ConnectTimeoutException ||
                                cause instanceof reactor.netty.http.client.PrematureCloseException;
                    }

                    return false;
                })
                .onRetryExhaustedThrow((retryBackoffSpec, retrySignal) -> {
                    log.error("Retry exhausted after {} attempts", retrySignal.totalRetries());
                    return retrySignal.failure();
                });
    }

    /**
     * Error handling filter for standardized error responses.
     */
    private ExchangeFilterFunction errorHandlingFilter() {
        return ExchangeFilterFunction.ofResponseProcessor(response -> {
            if (response.statusCode().is4xxClientError() || response.statusCode().is5xxServerError()) {
                log.warn("HTTP error response: {} {}", response.statusCode().value(), response.statusCode());

                // You can customize error handling here based on status codes
                if (response.statusCode().value() == 401) {
                    log.error("Authentication failed - check proxy credentials");
                } else if (response.statusCode().value() == 407) {
                    log.error("Proxy authentication required");
                } else if (response.statusCode().value() >= 500) {
                    log.error("Server error occurred: {}", response.statusCode());
                }
            }
            return Mono.just(response);
        });
    }

    /**
     * Timeout filter with request-specific timeout handling.
     */
    private ExchangeFilterFunction timeoutFilter(Duration timeout) {
        return (request, next) -> {
            return next.exchange(request)
                    .timeout(timeout)
                    .doOnError(java.util.concurrent.TimeoutException.class, ex -> {
                        log.error("Request timed out after {}ms: {} {}",
                                timeout.toMillis(), request.method(), request.url());
                    })
                    .onErrorMap(java.util.concurrent.TimeoutException.class, ex ->
                            new WebClientRequestException(
                                    new RuntimeException("Request timeout after " + timeout.toMillis() + "ms", ex),
                                    request.method(),
                                    request.url(),
                                    request.headers()
                            ));
        };
    }

    /**
     * Creates a WebClient without proxy for direct connections.
     * Useful for fallback scenarios or non-proxy endpoints.
     */
    public WebClient buildDirect(int timeoutMs) {
        log.info("Building WebClient without proxy (timeout: {}ms)", timeoutMs);

        ConnectionProvider connectionProvider = createConnectionProvider();

        HttpClient httpClient = HttpClient.create(connectionProvider)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, timeoutMs)
                .option(ChannelOption.SO_KEEPALIVE, true)
                .option(ChannelOption.TCP_NODELAY, true)
                .responseTimeout(Duration.ofMillis(timeoutMs))
                .doOnConnected(conn -> {
                    conn.addHandlerLast(new ReadTimeoutHandler(timeoutMs, TimeUnit.MILLISECONDS));
                    conn.addHandlerLast(new WriteTimeoutHandler(timeoutMs, TimeUnit.MILLISECONDS));
                });

        ReactorClientHttpConnector connector = new ReactorClientHttpConnector(httpClient);

        ExchangeStrategies strategies = ExchangeStrategies.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(MAX_IN_MEMORY_SIZE))
                .build();

        return WebClient.builder()
                .clientConnector(connector)
                .exchangeStrategies(strategies)
                .filter(loggingFilter())
                .filter(retryFilter())
                .filter(errorHandlingFilter())
                .filter(timeoutFilter(Duration.ofMillis(timeoutMs)))
                .build();
    }
}