package org.timmy.knowcommute.extraction;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "readcast.extraction")
public record ExtractionProperties(
        Duration connectTimeout,
        Duration readTimeout,
        int maxResponseBytes,
        int maxRedirects,
        int minimumWords,
        String userAgent) {
}
