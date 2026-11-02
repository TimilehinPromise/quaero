package org.timmy.knowcommute.figure;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "readcast.figures")
public record FigureProperties(
        boolean enabled,
        String model,
        Duration timeout,
        int maxOutputTokens,
        int maxPerArticle,
        int minDimension,
        int maxResponseBytes) {
}
