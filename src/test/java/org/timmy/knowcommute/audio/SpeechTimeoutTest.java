package org.timmy.knowcommute.audio;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.audio.tts.TextToSpeechModel;
import org.springframework.ai.model.openai.autoconfigure.OpenAiAudioSpeechAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * Guards the reason {@link SpeechModelConfig} builds the speech model by hand.
 *
 * <p>Synthesis runs slower than realtime, so anything past a couple of hundred words takes
 * longer than a minute. Spring AI 2.0.1 cannot be configured past 60 seconds:
 * {@code OpenAiAudioSpeechProperties.toOptions()} does not map {@code timeout}, so the options
 * keep their 60s default, the model sets that as a <em>per-request</em> timeout, and a
 * per-request value overrides the client-level one. Configuring the client is therefore
 * futile — including via an {@code OpenAiHttpClientBuilderCustomizer}.
 *
 * <p>Both behaviours are asserted on the wire. If the autoconfiguration test ever fails,
 * upstream has fixed the bug and the hand-built model can go.
 */
class SpeechTimeoutTest {

    private HttpServer server;
    private final Map<String, List<String>> capturedHeaders = new ConcurrentHashMap<>();

    private String startServer() {
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        server.createContext("/v1/audio/speech", exchange -> {
            capturedHeaders.putAll(exchange.getRequestHeaders());
            byte[] body = new byte[128];
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/v1";
    }

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    private String header(String name) {
        return capturedHeaders.entrySet().stream()
                .filter(entry -> entry.getKey().equalsIgnoreCase(name))
                .flatMap(entry -> entry.getValue().stream())
                .findFirst()
                .orElse(null);
    }

    @Test
    void ourSpeechModelSendsTheConfiguredTimeout() {
        String baseUrl = startServer();

        new ApplicationContextRunner()
                .withUserConfiguration(SpeechModelConfig.class)
                .withPropertyValues(
                        "readcast.tts.base-url=" + baseUrl,
                        "readcast.tts.api-key=not-required",
                        "readcast.tts.model=kokoro",
                        "readcast.tts.voice=af_bella",
                        "readcast.tts.response-format=mp3",
                        "readcast.tts.speed=1.0",
                        "readcast.tts.max-concurrent=1",
                        "readcast.tts.timeout=15m")
                .run(context -> {
                    context.getBean(TextToSpeechModel.class).call("hello");

                    assertThat(header("X-Stainless-Timeout")).isEqualTo("900");
                    assertThat(header("X-Stainless-Read-Timeout")).isEqualTo("900");
                });
    }

    @Test
    void ourSpeechModelSendsBasicAuthAsTheOnlyAuthorizationHeader() {
        String baseUrl = startServer();

        new ApplicationContextRunner()
                .withUserConfiguration(SpeechModelConfig.class)
                .withPropertyValues(
                        "readcast.tts.base-url=" + baseUrl,
                        "readcast.tts.api-key=ignored-bearer",
                        "readcast.tts.model=kokoro",
                        "readcast.tts.timeout=1m",
                        "readcast.tts.max-concurrent=1",
                        "readcast.tts.basic-auth-user=user",
                        "readcast.tts.basic-auth-password=secret")
                .run(context -> {
                    context.getBean(TextToSpeechModel.class).call("hello");

                    List<String> authorization = capturedHeaders.entrySet().stream()
                            .filter(entry -> entry.getKey().equalsIgnoreCase("Authorization"))
                            .flatMap(entry -> entry.getValue().stream())
                            .toList();

                    // A duplicate Authorization header is answered with 400 by nginx.
                    assertThat(authorization).containsExactly("Basic dXNlcjpzZWNyZXQ=");
                });
    }

    @Test
    void autoconfigurationStillIgnoresTheTimeoutProperty() {
        String baseUrl = startServer();

        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(OpenAiAudioSpeechAutoConfiguration.class))
                .withPropertyValues(
                        "spring.ai.openai.audio.speech.base-url=" + baseUrl,
                        "spring.ai.openai.audio.speech.api-key=not-required",
                        "spring.ai.openai.audio.speech.model=kokoro",
                        "spring.ai.openai.audio.speech.timeout=15m")
                .run(context -> {
                    context.getBean(TextToSpeechModel.class).call("hello");

                    assertThat(header("X-Stainless-Timeout"))
                            .as("upstream bug: toOptions() drops timeout, so 60s wins. "
                                    + "If this is no longer 60, SpeechModelConfig can be deleted.")
                            .isEqualTo("60");
                });
    }
}
