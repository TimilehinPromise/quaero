package org.timmy.knowcommute.pipeline;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "readcast.pipeline")
public record PipelineProperties(
        boolean enabled,
        Duration pollInterval,
        int batchSize,
        int concurrency,
        int maxAttempts,
        Duration lease,
        Duration initialBackoff) {
}
