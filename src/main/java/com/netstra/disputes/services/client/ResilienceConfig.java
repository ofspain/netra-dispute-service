package com.netstra.disputes.services.client;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
public class ResilienceConfig {

    @Bean
    public TimeLimiter timeLimiter(TimeLimiterRegistry timeLimiterRegistry) {
        return timeLimiterRegistry.timeLimiter("external-service-tl");
    }

    @Bean
    public Retry retry(RetryRegistry retryRegistry) {
        return retryRegistry.retry("external-service-retry");
    }

    @Bean
    public CircuitBreaker circuitBreaker(CircuitBreakerRegistry circuitBreakerRegistry) {
        return circuitBreakerRegistry.circuitBreaker("external-service-cb");
    }

    @Bean
    public ExecutorService timeLimiterExecutor() {
        return Executors.newCachedThreadPool(); // Used by TimeLimiter
    }
}
