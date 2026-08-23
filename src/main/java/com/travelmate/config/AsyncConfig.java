package com.travelmate.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * AI 讲解生成等耗时任务的默认异步线程池(dev 环境不依赖 MQ)。
 */
@Configuration
public class AsyncConfig {

    @Bean("guideExecutor")
    public Executor guideExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(16);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("guide-async-");
        executor.initialize();
        return executor;
    }
}
