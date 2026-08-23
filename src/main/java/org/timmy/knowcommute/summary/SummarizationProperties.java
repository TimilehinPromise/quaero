package org.timmy.knowcommute.summary;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "readcast.summarization")
public record SummarizationProperties(int maxInputWords) {
}
