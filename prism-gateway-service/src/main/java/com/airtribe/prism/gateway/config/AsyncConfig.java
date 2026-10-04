package com.airtribe.prism.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.support.TaskExecutorAdapter;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Bounded worker pool for the usage-logging producer/consumer pipeline (see
 * UsageLoggingService). Deliberately bounded rather than unbounded: under a write
 * storm we want callers to feel back-pressure (CallerRunsPolicy briefly borrows the
 * calling thread) rather than let an unbounded queue grow without limit and OOM the
 * gateway - a classic bulkhead pattern applied to async logging.
 */
@Configuration
public class AsyncConfig {

    @Bean("usageLoggingExecutor")
    public Executor usageLoggingExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(2000);
        executor.setThreadNamePrefix("usage-log-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
