package com.company.autoremediate.config;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ExecutorConfig {

    /** Builds are heavy, so concurrency is bounded (default: one job at a time). */
    @Bean(name = "remediationExecutor", destroyMethod = "shutdownNow")
    public ExecutorService remediationExecutor(RemediationProperties props) {
        return Executors.newFixedThreadPool(Math.max(1, props.maxConcurrentJobs()));
    }
}
