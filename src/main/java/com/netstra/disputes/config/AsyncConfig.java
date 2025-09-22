package com.netstra.disputes.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.*;

@Configuration
public class AsyncConfig {

    @Bean(name = "restClientExecutorService")
    public ExecutorService restClientExecutorService() {
        int corePoolSize = Runtime.getRuntime().availableProcessors();
        int maxPoolSize = corePoolSize * 2; // or tune based on load
        long keepAliveTime = 60L;

        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                corePoolSize,
                maxPoolSize,
                keepAliveTime,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(500), // queue for high load
                new ThreadFactory() {
                    private final ThreadFactory defaultFactory = Executors.defaultThreadFactory();
                    @Override
                    public Thread newThread(Runnable r) {
                        Thread t = defaultFactory.newThread(r);
                        t.setName("rest-client-executor-" + t.getId());
                        t.setDaemon(true);
                        return t;
                    }
                },
                new ThreadPoolExecutor.CallerRunsPolicy() // fallback policy
        );
        executor.allowCoreThreadTimeOut(true);
        return executor;
    }
}

