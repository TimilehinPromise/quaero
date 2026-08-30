package org.timmy.knowcommute.audio;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.springframework.ai.audio.tts.TextToSpeechModel;
import org.springframework.ai.openai.OpenAiAudioSpeechModel;
import org.springframework.ai.openai.OpenAiAudioSpeechOptions;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Builds the speech model by hand rather than letting the OpenAI starter autoconfigure it.
 *
 * <p>Two reasons. The starter's {@code spring.ai.openai.audio.speech.timeout} property does not
 * reach the client — observed on the wire as {@code X-Stainless-Timeout: 60} however it is set —
 * and 60 seconds fails every summary longer than roughly two hundred words, because synthesis
 * runs slower than realtime. And an authenticating interceptor registered as a bean applies to
 * every OpenAI-flavoured client in the context, including the one pointed at a different
 * provider for chat; building this one explicitly keeps its credentials to itself.
 */
@Configuration
@EnableConfigurationProperties(TtsProperties.class)
public class SpeechModelConfig {

    @Bean
    TextToSpeechModel textToSpeechModel(TtsProperties properties) {
        OpenAiAudioSpeechOptions options = OpenAiAudioSpeechOptions.builder()
                .baseUrl(properties.baseUrl())
                .apiKey(properties.apiKey())
                .model(properties.model())
                .voice(properties.voice())
                .responseFormat(properties.responseFormat())
                .speed(properties.speed())
                .timeout(properties.timeout())
                .build();

        var builder = OpenAiAudioSpeechModel.builder().options(options);
        if (properties.hasBasicAuth()) {
            builder.httpClientBuilderCustomizer(client -> client
                    .timeout(properties.timeout())
                    .interceptor(chain -> chain.proceed(chain.request().newBuilder()
                            .header("Authorization", basicAuth(properties))
                            .build())));
        } else {
            builder.httpClientBuilderCustomizer(client -> client.timeout(properties.timeout()));
        }
        return builder.build();
    }

    /**
     * Replaces rather than adds: Spring AI always sends its own {@code Authorization: Bearer},
     * and a proxy sees two Authorization headers as a malformed request and answers 400.
     */
    private String basicAuth(TtsProperties properties) {
        String pair = properties.basicAuthUser() + ":" + properties.basicAuthPassword();
        return "Basic " + Base64.getEncoder().encodeToString(pair.getBytes(StandardCharsets.UTF_8));
    }
}
