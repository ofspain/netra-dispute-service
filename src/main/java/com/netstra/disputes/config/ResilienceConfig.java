package com.netstra.disputes.config;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.timelimiter.*;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class ResilienceConfig {

    @Bean
    public TimeLimiter timeLimiter() {
        TimeLimiterConfig config = TimeLimiterConfig.custom()
                .timeoutDuration(Duration.ofSeconds(5)) // max allowed execution time
                .cancelRunningFuture(true)
                .build();

        return TimeLimiter.of("defaultTimeLimiter", config);
    }

    @Bean
    public RetryRegistry retryRegistry() {
        RetryConfig config = RetryConfig.custom()
                .maxAttempts(3)
                .waitDuration(Duration.ofMillis(500))
                .build();

        return RetryRegistry.of(config);
    }

    @Bean
    public CircuitBreakerRegistry circuitBreakerRegistry() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .failureRateThreshold(50) // open circuit if 50% of requests fail
                .waitDurationInOpenState(Duration.ofSeconds(10)) // time before trying half-open
                .slidingWindowSize(20) // number of calls to evaluate failure rate
                .build();

        return CircuitBreakerRegistry.of(config);
    }

    @Bean
    public Counter customRequestsCounter(MeterRegistry meterRegistry) {
        Counter counter = meterRegistry.counter("custom.requests.total");
        counter.increment(); // optional: initial increment
        return counter;
    }

    @Bean
    public MeterRegistry meterRegistry() {
        return new SimpleMeterRegistry();
    }
}

