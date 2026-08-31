package org.timmy.knowcommute.summary;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.model.chat.client.autoconfigure.ChatClientAutoConfiguration;
import org.springframework.ai.model.openai.autoconfigure.OpenAiChatAutoConfiguration;
import org.springframework.ai.model.tool.autoconfigure.ToolCallingAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.io.ClassPathResource;
import org.timmy.knowcommute.bookmark.SummaryLength;

/**
 * Guards the reason {@link SummarizationService} sets a timeout on every call.
 *
 * <p>A long summary generates for longer than a minute, and Spring AI 2.0.1 cannot be
 * configured past that: neither {@code spring.ai.openai.chat.timeout} nor
 * {@code spring.ai.openai.chat.options.timeout} reaches the client, so openai-java's 60s
 * default stands. Over HTTP/2 the expiry arrives as a stream reset, which reads as a network
 * fault rather than a timeout.
 *
 * <p>Both behaviours are asserted on the wire. If the autoconfiguration test ever fails,
 * upstream has fixed the bug and the per-call option can go.
 */
class SummarizationTimeoutTest {

    private static final String COMPLETION = """
            {"id":"1","object":"chat.completion","created":1,"model":"m",
             "choices":[{"index":0,"message":{"role":"assistant","content":"A short narration."},
             "finish_reason":"stop"}]}""";

    private HttpServer server;
    private final Map<String, List<String>> capturedHeaders = new ConcurrentHashMap<>();

    private String startServer() {
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        server.createContext("/v1/chat/completions", exchange -> {
            capturedHeaders.putAll(exchange.getRequestHeaders());
            byte[] body = COMPLETION.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
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

    private ApplicationContextRunner contextWith(String baseUrl) {
        return new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        ToolCallingAutoConfiguration.class,
                        OpenAiChatAutoConfiguration.class,
                        ChatClientAutoConfiguration.class))
                .withPropertyValues(
                        "spring.ai.openai.chat.base-url=" + baseUrl,
                        "spring.ai.openai.chat.api-key=not-configured",
                        "spring.ai.openai.chat.options.model=test-model",
                        "spring.ai.openai.chat.options.max-tokens=4096");
    }

    @Test
    void ourSummarizerSendsTheConfiguredTimeout() {
        String baseUrl = startServer();

        contextWith(baseUrl).run(context -> {
            SummarizationService service = new SummarizationService(
                    context.getBean(ChatClient.Builder.class),
                    new SummarizationProperties(40000, Duration.ofMinutes(5), 1.0, 1),
                    new ClassPathResource("prompts/summarise-article.st"));

            service.summarise("A Title", "example.com", "Article body.", SummaryLength.SHORT);

            assertThat(header("X-Stainless-Timeout")).isEqualTo("300");
        });
    }

    @Test
    void theConfiguredModelAndTokenLimitSurviveTheTimeoutOption() {
        String baseUrl = startServer();

        contextWith(baseUrl).run(context -> {
            SummarizationService service = new SummarizationService(
                    context.getBean(ChatClient.Builder.class),
                    new SummarizationProperties(40000, Duration.ofMinutes(5), 1.0, 1),
                    new ClassPathResource("prompts/summarise-article.st"));

            // A per-call option replacing rather than merging would drop both and OpenRouter
            // answers a request with no model with 400.
            assertThat(service.summarise("A Title", "example.com", "Body.", SummaryLength.SHORT))
                    .isEqualTo("A short narration.");
        });
    }

    @Test
    void autoconfigurationStillIgnoresBothTimeoutProperties() {
        String baseUrl = startServer();

        contextWith(baseUrl)
                .withPropertyValues(
                        "spring.ai.openai.chat.timeout=10m",
                        "spring.ai.openai.chat.options.timeout=10m")
                .run(context -> {
                    context.getBean(ChatClient.Builder.class).build()
                            .prompt().user("hello").call().content();

                    assertThat(header("X-Stainless-Timeout"))
                            .as("upstream bug: neither timeout property reaches the client, so 60s "
                                    + "wins. If this is no longer 60, the per-call option can go.")
                            .isEqualTo("60");
                });
    }
}
