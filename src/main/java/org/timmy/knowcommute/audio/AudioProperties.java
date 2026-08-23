package org.timmy.knowcommute.audio;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "readcast.audio")
public record AudioProperties(
        String bucket,
        String endpoint,
        String accessKey,
        String secretKey,
        String region,
        Duration presignTtl,
        Duration retention) {
}
