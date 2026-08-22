package org.timmy.knowcommute.pipeline;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(PipelineProperties.class)
public class PipelineConfig {

    @Bean(destroyMethod = "shutdown")
    ExecutorService pipelineExecutor(PipelineProperties properties) {
        return Executors.newFixedThreadPool(properties.concurrency());
    }
}
