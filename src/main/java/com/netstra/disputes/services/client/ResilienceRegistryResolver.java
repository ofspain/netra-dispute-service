package com.netstra.disputes.services.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netra.commons.models.EndpointConfig;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.function.Supplier;


@Slf4j
public class ResilienceRegistryResolver {

    private final RetryRegistry retryRegistry;

    public ResilienceRegistryResolver() {
        this.retryRegistry = RetryRegistry.ofDefaults();
    }

    public <T> Supplier<T> decorateWithRetryAndFallback(String id, Supplier<T> original,
                                                        EndpointConfig config, Class<T> type) {
        // Register Retry
        Retry retry = registerRetry(id, config.getRetryConfig());

        Supplier<T> retryable = Retry.decorateSupplier(retry, original);

        // Fallback Logic
        EndpointConfig.FallbackConfig fallbackConfig = config.getFallbackConfig();
        if (fallbackConfig != null && fallbackConfig.getType() != null) {
            return decorateWithFallback(retryable, fallbackConfig, type);
        }

        return retryable;
    }

    private Retry registerRetry(String id, EndpointConfig.RetryConfig cfg) {
        RetryConfig rCfg = RetryConfig.custom()
                .maxAttempts(cfg.getMaxAttempts())
                .waitDuration(Duration.ofMillis(cfg.getInitialDelayMillis()))
                .intervalFunction(IntervalFunction.ofExponentialBackoff(
                        Duration.ofMillis(cfg.getInitialDelayMillis()),
                        cfg.getMultiplier(),
                        Duration.ofMillis(cfg.getMaxDelayMillis())
                ))
                .build();

        return retryRegistry.retry(id, rCfg);
    }

    private <T> Supplier<T> decorateWithFallback(Supplier<T> retryable,
                                                 EndpointConfig.FallbackConfig fallback,
                                                 Class<T> type) {
        switch (fallback.getType()) {
            case STATIC_RESPONSE:
                return () -> {
                    try {
                        return retryable.get();
                    } catch (Exception e) {
                        log.warn("Fallback triggered: static response used", e);
                        return new ObjectMapper().convertValue(fallback.getValue(), type);
                    }
                };

            case REDIRECT_ENDPOINT:
                ////todo: will redirect to the specified url
                throw new UnsupportedOperationException("REDIRECT_ENDPOINT fallback is not yet supported.");

            case EXCEPTION:
                return () -> {
                    try {
                        return retryable.get();
                    } catch (Exception e) {
                        throw new IllegalStateException("Fallback exception triggered.", e);
                    }
                };

            default:
                return retryable;
        }
    }
}

