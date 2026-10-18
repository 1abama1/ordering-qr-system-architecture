package com.restaurant.ordering.realtime.config;
import org.springframework.context.annotation.*;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
@Configuration
public class RealtimeExecutorConfig {
    @Bean public ThreadPoolTaskExecutor realtimeExecutor() {
        var e = new ThreadPoolTaskExecutor(); e.setCorePoolSize(2); e.setMaxPoolSize(4); e.setQueueCapacity(256);
        e.setThreadNamePrefix("realtime-"); e.setWaitForTasksToCompleteOnShutdown(true); e.setAwaitTerminationSeconds(10); return e;
    }
}
