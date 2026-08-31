package org.timmy.knowcommute.audio;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "readcast.tts")
public record TtsProperties(
        String baseUrl,
        String apiKey,
        String model,
        String voice,
        String responseFormat,
        Double speed,
        Duration timeout,
        int maxConcurrent,
        int chunkChars,
        String basicAuthUser,
        String basicAuthPassword) {

    boolean hasBasicAuth() {
        return basicAuthUser != null && !basicAuthUser.isBlank();
    }
}
