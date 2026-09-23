package org.timmy.knowcommute.extraction;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "readcast.reader.jina")
public record JinaReaderProperties(
        String baseUrl,
        String apiKey,
        String engine) {
}
