package org.timmy.knowcommute.audio;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.ai.openai.OpenAiAudioSpeechModel;
import org.springframework.ai.openai.OpenAiAudioSpeechOptions;

/**
 * Manual smoke test against a real Kokoro deployment. Disabled by default: needs network and
 * credentials. Run with KOKORO_BASE_URL (including /v1), KOKORO_USER and KOKORO_PASSWORD set.
 */
@Disabled("Manual: requires a reachable Kokoro and credentials")
class KokoroSmokeTest {

    private static String env(String name) {
        String value = System.getenv(name);
        assertThat(value).as(name + " must be set to run this test").isNotBlank();
        return value;
    }

    @Test
    void synthesisesSpeechThroughBasicAuth() {
        String credentials = "Basic " + Base64.getEncoder().encodeToString(
                (env("KOKORO_USER") + ":" + env("KOKORO_PASSWORD")).getBytes(StandardCharsets.UTF_8));

        OpenAiAudioSpeechOptions options = OpenAiAudioSpeechOptions.builder()
                .baseUrl(env("KOKORO_BASE_URL"))
                .apiKey("not-required")
                .model("kokoro")
                .voice("af_bella")
                .responseFormat("mp3")
                .build();

        byte[] mp3 = OpenAiAudioSpeechModel.builder()
                .options(options)
                .httpClientBuilderCustomizer(builder -> builder.interceptor(chain ->
                        chain.proceed(chain.request().newBuilder()
                                .header("Authorization", credentials)
                                .build())))
                .build()
                .call("This is a short smoke test of the ReadCast narration pipeline.");

        assertThat(mp3).isNotEmpty();
        assertThat(Mp3Duration.seconds(mp3)).isPositive();
        System.out.println("SMOKE-OK bytes=" + mp3.length + " seconds=" + Mp3Duration.seconds(mp3));
    }
}
