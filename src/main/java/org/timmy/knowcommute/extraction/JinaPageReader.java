package org.timmy.knowcommute.extraction;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.time.Duration;
import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

/**
 * Renders pages through Jina's Reader API ({@code https://r.jina.ai}), which fetches and
 * executes the page and returns markdown. Active only when {@code readcast.reader.provider=jina};
 * another provider is another {@link PageReader} bean, selected by name.
 *
 * <p>Privacy note: enabling this route sends bookmarked URLs to Jina. The API is only ever
 * called for pages a direct fetch found no readable text in.
 */
@Component
@ConditionalOnExpression("'${readcast.reader.enabled:false}' == 'true' "
        + "&& '${readcast.reader.provider:}' == 'jina'")
public class JinaPageReader implements PageReader {

    private final WebClient webClient;
    private final JinaReaderProperties properties;
    private final ObjectMapper json = new ObjectMapper();
    private final Duration timeout;

    public JinaPageReader(@Qualifier("webClientBuilder") WebClient.Builder webClientBuilder,
                          JinaReaderProperties properties, ReaderProperties readerProperties) {
        if (properties.apiKey().isBlank()) {
            throw new IllegalStateException("readcast.reader.provider=jina requires an API key: "
                    + "set readcast.reader.jina.api-key or the JINA_API_KEY env var");
        }
        this.properties = properties;
        this.timeout = readerProperties.timeout();
        this.webClient = webClientBuilder
                .clientConnector(new ReactorClientHttpConnector(
                        HttpClient.create().responseTimeout(timeout)))
                .exchangeStrategies(ExchangeStrategies.builder()
                        .codecs(codecs -> codecs.defaultCodecs()
                                .maxInMemorySize(readerProperties.maxResponseBytes()))
                        .build())
                .build();
    }

    @Override
    public String provider() {
        return "jina";
    }

    @Override
    public RenderedPage read(URI url) {
        String body = webClient.post()
                .uri(URI.create(properties.baseUrl()))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.apiKey())
                .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .header("X-Engine", properties.engine())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("url", url.toString()))
                .exchangeToMono(response -> {
                    HttpStatusCode status = response.statusCode();
                    if (status.is2xxSuccessful()) {
                        return response.bodyToMono(String.class).defaultIfEmpty("");
                    }
                    return response.releaseBody().then(reactor.core.publisher.Mono.error(
                            serviceError(status.value())));
                })
                .block(timeout);
        return parse(body);
    }

    /**
     * A 402 means the account is out of credits — it will not fix itself within a retry
     * backoff, so it fails on the spot rather than after the whole ladder. A 429 may lift;
     * a 5xx may recover. A 4xx about the URL itself is deterministic for that URL.
     */
    private ExtractionException serviceError(int status) {
        if (status == 402) {
            return new ExtractionException("Jina Reader account is out of credits (HTTP 402); "
                    + "top it up or disable readcast.reader", false);
        }
        if (status == 429) {
            return new ExtractionException("Jina Reader rate-limited the request (HTTP 429)", true);
        }
        if (status >= 500) {
            return new ExtractionException("Jina Reader failed with HTTP " + status, true);
        }
        return new ExtractionException("Jina Reader could not read the page (HTTP " + status + ")", false);
    }

    private RenderedPage parse(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            JsonNode data = json.readTree(body).path("data");
            String content = data.path("content").asText("");
            if (content.isBlank()) {
                return null;
            }
            String title = data.path("title").asText(null);
            return new RenderedPage(title == null || title.isBlank() ? null : title, null, content);
        } catch (JsonProcessingException e) {
            throw new ExtractionException("Jina Reader returned a malformed body", e, false);
        }
    }
}
