package com.openmanus.infra.config;

import com.alibaba.ttl.threadpool.TtlExecutors;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * 异步执行器配置类
 * 
 * 配置异步任务执行器，支持MDC上下文在异步线程间传递
 * 使用TransmittableThreadLocal (TTL)确保日志追踪的连续性
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    public static final String ASYNC_EXECUTOR_NAME = "asyncExecutor";
    
    private static final int CORE_POOL_SIZE = 5;
    private static final int MAX_POOL_SIZE = 20;
    private static final int QUEUE_CAPACITY = 100;

    @Bean(name = ASYNC_EXECUTOR_NAME)
    public Executor asyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(CORE_POOL_SIZE);
        executor.setMaxPoolSize(MAX_POOL_SIZE);
        executor.setQueueCapacity(QUEUE_CAPACITY);
        executor.setThreadNamePrefix("async-task-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.initialize();
        
        // 使用TTL包装线程池，支持MDC跨线程传递
        return TtlExecutors.getTtlExecutor(executor);
    }
}
