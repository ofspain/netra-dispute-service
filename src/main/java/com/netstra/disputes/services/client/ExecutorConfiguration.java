package com.netstra.disputes.services.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

@Configuration
public class ExecutorConfiguration {

    @Value("${app.timelimiter.scheduler.pool-size:2}")
    private int timeLimiterSchedulerPoolSize;

    @Bean("timeLimiterScheduler")
    public ScheduledExecutorService timeLimiterScheduler() {
        ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(
                timeLimiterSchedulerPoolSize,
                new TimeLimiterThreadFactory()
        );

        // Configure the executor
        executor.setRemoveOnCancelPolicy(true);
        executor.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);

        return executor;
    }

    // Custom thread factory for better thread naming and debugging
    private static class TimeLimiterThreadFactory implements ThreadFactory {
        private final AtomicInteger threadNumber = new AtomicInteger(1);
        private final String namePrefix = "timelimiter-scheduler-";

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, namePrefix + threadNumber.getAndIncrement());
            t.setDaemon(true); // Don't prevent JVM shutdown
            if (t.getPriority() != Thread.NORM_PRIORITY) {
                t.setPriority(Thread.NORM_PRIORITY);
            }
            return t;
        }
    }

    // Optional: If you also want to configure your existing timeLimiterExecutor
    @Value("${app.timelimiter.executor.pool-size:10}")
    private int timeLimiterExecutorPoolSize;

    @Bean("timeLimiterExecutor")
    public ScheduledExecutorService timeLimiterExecutor() {
        // Using ScheduledThreadPoolExecutor which implements both
        // ExecutorService and ScheduledExecutorService
        return Executors.newScheduledThreadPool(
                timeLimiterExecutorPoolSize,
                new TimeLimiterExecutorThreadFactory()
        );
    }

    private static class TimeLimiterExecutorThreadFactory implements ThreadFactory {
        private final AtomicInteger threadNumber = new AtomicInteger(1);
        private final String namePrefix = "timelimiter-executor-";

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, namePrefix + threadNumber.getAndIncrement());
            t.setDaemon(true);
            if (t.getPriority() != Thread.NORM_PRIORITY) {
                t.setPriority(Thread.NORM_PRIORITY);
            }
            return t;
        }
    }
}
