package org.timmy.knowcommute.extraction;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.timmy.knowcommute.bookmark.InvalidUrlException;
import org.timmy.knowcommute.bookmark.UrlValidator;
import org.timmy.knowcommute.security.PrivateAddressPolicy;
import reactor.netty.http.client.HttpClient;

@Component
public class ArticleFetcher {

    private static final Set<Integer> BLOCKING_STATUSES = Set.of(401, 402, 403, 407, 429, 451);

    private final WebClient webClient;
    private final UrlValidator urlValidator;
    private final ExtractionProperties properties;

    public ArticleFetcher(UrlValidator urlValidator, PrivateAddressPolicy addressPolicy,
                          ExtractionProperties properties) {
        this.urlValidator = urlValidator;
        this.properties = properties;
        this.webClient = WebClient.builder()
                .clientConnector(new org.springframework.http.client.reactive.ReactorClientHttpConnector(
                        httpClient(addressPolicy, properties)))
                .build();
    }

    /**
     * Redirects are followed by hand so every hop is re-validated. Auto-following would let a
     * public URL redirect straight into the private network on hop two.
     */
    public FetchedPage fetch(URI url) {
        URI current = url;
        for (int hop = 0; hop <= properties.maxRedirects(); hop++) {
            urlValidator.validate(current.toString());
            Redirected redirected = attempt(current);
            if (redirected.location() == null) {
                return new FetchedPage(current, redirected.body());
            }
            current = current.resolve(redirected.location());
        }
        throw new ExtractionException("Exceeded " + properties.maxRedirects() + " redirects");
    }

    private Redirected attempt(URI url) {
        return webClient.get()
                .uri(url)
                .header(HttpHeaders.USER_AGENT, properties.userAgent())
                .accept(MediaType.TEXT_HTML, MediaType.APPLICATION_XHTML_XML)
                .exchangeToMono(response -> {
                    HttpStatusCode status = response.statusCode();
                    if (status.is3xxRedirection()) {
                        String location = response.headers().header(HttpHeaders.LOCATION)
                                .stream().findFirst().orElse(null);
                        return response.releaseBody()
                                .thenReturn(new Redirected(location, null));
                    }
                    if (BLOCKING_STATUSES.contains(status.value())) {
                        return response.releaseBody().then(reactor.core.publisher.Mono.error(
                                new SiteBlockedException("Site refused the request (HTTP "
                                        + status.value() + ")")));
                    }
                    if (status.isError()) {
                        return response.releaseBody().then(reactor.core.publisher.Mono.error(
                                new ExtractionException("Fetch failed with HTTP " + status.value())));
                    }
                    return response.bodyToMono(String.class)
                            .defaultIfEmpty("")
                            .map(body -> new Redirected(null, body));
                })
                .block(properties.readTimeout().plusSeconds(5));
    }

    private HttpClient httpClient(PrivateAddressPolicy addressPolicy, ExtractionProperties properties) {
        return HttpClient.create()
                .followRedirect(false)
                .compress(true)
                .responseTimeout(properties.readTimeout())
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS,
                        (int) properties.connectTimeout().toMillis())
                .doOnConnected(connection -> {
                    guardAgainstRebinding(addressPolicy, connection.channel().remoteAddress());
                    connection.addHandlerLast(new ReadTimeoutHandler(
                            properties.readTimeout().toSeconds(), TimeUnit.SECONDS));
                })
                .httpResponseDecoder(spec -> spec.maxInitialLineLength(8192))
                .responseTimeout(properties.readTimeout());
    }

    /**
     * Closes the DNS-rebinding window left open by {@link UrlValidator}: the validator checks
     * what a hostname resolves to, but the client resolves it again, and a hostile resolver can
     * answer differently. This checks the address actually connected to, before any request
     * bytes are written.
     */
    private void guardAgainstRebinding(PrivateAddressPolicy addressPolicy, java.net.SocketAddress remote) {
        if (remote instanceof InetSocketAddress inet && inet.getAddress() != null
                && addressPolicy.isPrivate(inet.getAddress())) {
            throw new InvalidUrlException(
                    "Connection resolved to a non-public address: " + inet.getAddress().getHostAddress());
        }
    }

    private record Redirected(String location, String body) {
    }

    static boolean isBlockingStatus(HttpStatus status) {
        return BLOCKING_STATUSES.contains(status.value());
    }
}
