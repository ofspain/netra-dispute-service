package com.netstra.disputes.services.client.util.generalized;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netra.commons.models.endpoint.*;
import com.netra.commons.trace.AuditLogger;
import com.netstra.disputes.services.client.util.EndpointConfigIdentity;
import com.netstra.disputes.services.client.util.ParamsDTO;
import com.netstra.disputes.services.client.util.ResolvedRequest;
import com.netstra.disputes.services.client.util.Utility;
import com.netstra.disputes.services.client.vault.AwsSecretsManagerVault;
import com.netstra.disputes.services.client.vault.VaultManager;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Supplier;

@Component
//@RequiredArgsConstructor
public class GeneralizedRestClientExecutor {
    private final DomainRestClientFactory clientFactory;
    private final ExecutorService executorService;
    private final AuditLogger auditLogger = new AuditLogger();
    private final ObjectMapper objectMapper;

    private final VaultManager vaultManager;

    @Autowired
    public GeneralizedRestClientExecutor(
            DomainRestClientFactory clientFactory,
            @Qualifier("restClientExecutorService") ExecutorService executorService,
            ObjectMapper objectMapper,
            @Qualifier("awsVault")AwsSecretsManagerVault vaultManager) {
        this.clientFactory = clientFactory;
        this.executorService = executorService;
        this.objectMapper = objectMapper;
        this.vaultManager = vaultManager;

    }

    /**
     * Executes a request for a given endpoint and operation.
     */
    public <T> T execute(EndpointConfig config, EndpointConfig.OperationType operationType,
                         ParameterizedTypeReference<T> responseType, Map<String,String> requestBodyContext, ParamsDTO paramsDTO,
                         boolean audit) {

        EndpointConfigIdentity identity = new EndpointConfigIdentity(config.getId(), config.getDomainType(), config.getDomainOwnerId(), config.getDomainCode());

        String id = Utility.calculateCachedRestClientId(identity);

        ResolvedRequest resolvedRequest = Utility.prepareEndpointRequest(
                config.getEndpoints().get(operationType),config,  requestBodyContext, paramsDTO,vaultManager
        );
        ResilienceConfig resilience = config.getResilience();


        // 1️⃣ Obtain domain-specific RestTemplate
        RestTemplate client = clientFactory.getClient(config);

        // 2️⃣ Resolve endpoint URL
        String url = resolvedRequest.resolvedUrl();

        // 3️⃣ Wrap request body and headers
        HttpEntity<?> entity = resolvedRequest.entity();

        // 4️⃣ Build per-domain resilience objects
        Retry retry = buildRetry(id, resilience.getRetry());
        CircuitBreaker cb = buildCircuitBreaker(id,resilience.getCircuitBreaker());
        TimeLimiter timeLimiter = buildTimeLimiter(config);

        // 5️⃣ Define actual REST call
        Supplier<T> callSupplier = () -> client.exchange(url, HttpMethod.POST, entity, responseType).getBody();

        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1); // or a shared pool


        // 6️⃣ Decorate call with resilience patterns
        Supplier<T> decorated = Retry.decorateSupplier(retry,
                CircuitBreaker.decorateSupplier(cb,
                        () -> timeLimiter.executeCompletionStage(scheduler,
                                        () -> CompletableFuture.supplyAsync(callSupplier, executorService))
                                .toCompletableFuture().join()));

        // 7️⃣ Execute with auditing
        long start = System.currentTimeMillis();
        String requestBodyStr;

        try {
            requestBodyStr = objectMapper.writeValueAsString(entity.getBody());
        } catch (JsonProcessingException e) {
            requestBodyStr = "Failed to serialize request body";
        }

        try {
            T result = decorated.get();
            if (audit) {
                auditLogger.logSuccess(
                        config.getDomainCode(),
                        HttpMethod.POST,
                        url,
                        entity.getHeaders(),
                        requestBodyStr,
                        result,
                        System.currentTimeMillis() - start
                );
            }
            return result;
        } catch (Exception e) {
            if (audit) {
                auditLogger.logFailure(
                        config.getDomainCode(),
                        HttpMethod.POST,
                        url,
                        entity.getHeaders(),
                        requestBodyStr,
                        e,
                        System.currentTimeMillis() - start
                );
            }
            throw e; // rethrow original exception
        }

    }

    // ---------------------- RESILIENCE BUILDERS -----------------------

    private Retry buildRetry(String id, com.netra.commons.models.endpoint.RetryConfig rc) {
        //com.netra.commons.models.endpoint.RetryConfig rc = config.getResilience().getRetry();

        RetryConfig retryConfig = RetryConfig.custom()
                .maxAttempts(rc.getMaxAttempts())
                .intervalFunction(attempt -> {
                    // Compute exponential backoff delay in milliseconds
                    long delay = (long) (rc.getInitialDelayMillis() * Math.pow(rc.getMultiplier(), attempt - 1));
                    // Cap delay at maxDelayMillis
                    return Math.min(delay, rc.getMaxDelayMillis());
                })
                .build();

        return Retry.of(id, retryConfig);
    }


    private CircuitBreaker buildCircuitBreaker(String id, com.netra.commons.models.endpoint.CircuitBreakerConfig circuitBreakerConfig) {
        CircuitBreakerConfig cbConfig = CircuitBreakerConfig.custom()
                .failureRateThreshold(50) // default 50%
                .minimumNumberOfCalls(1)
                .waitDurationInOpenState(Duration.ofMillis(circuitBreakerConfig.getResetTimeoutMillis()))
                .slidingWindowSize(circuitBreakerConfig.getFailureThreshold())
                .build();
        return CircuitBreaker.of(id, cbConfig);
    }

    private TimeLimiter buildTimeLimiter(EndpointConfig config) {
        // Use network timeout as fallback if needed
        int timeout = config.getNetwork() != null ? config.getNetwork().getTimeoutMillis() : 5000;
        TimeLimiterConfig tlConfig = TimeLimiterConfig.custom()
                .timeoutDuration(Duration.ofMillis(timeout))
                .cancelRunningFuture(true)
                .build();
        return TimeLimiter.of(tlConfig);
    }
}
