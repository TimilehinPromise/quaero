package org.timmy.knowcommute.summary;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "readcast.summarization")
public record SummarizationProperties(
        int maxInputWords, Duration timeout, double maxSourceFraction, int minWords) {
}
