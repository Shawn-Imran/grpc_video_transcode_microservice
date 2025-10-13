package com.example.transcoder.config;

import com.example.transcoder.service.VideoStorageService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Application configuration.
 */
@Configuration
@EnableAsync
public class AppConfig {

    /**
     * Configures the async task executor.
     *
     * @return Executor for async tasks
     */
    @Bean(name = "taskExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("TranscodeTask-");
        executor.initialize();
        return executor;
    }

    /**
     * Initializes storage directories.
     *
     * @param videoStorageService Video storage service
     * @return Initialized storage service
     */
    @Bean
    public VideoStorageService videoStorageServiceInit(VideoStorageService videoStorageService) {
        videoStorageService.init();
        return videoStorageService;
    }
}

