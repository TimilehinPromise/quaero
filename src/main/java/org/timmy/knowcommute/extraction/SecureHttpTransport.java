package org.timmy.knowcommute.extraction;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import org.timmy.knowcommute.bookmark.InvalidUrlException;
import org.timmy.knowcommute.bookmark.UrlValidator;
import org.timmy.knowcommute.security.PrivateAddressPolicy;
import reactor.netty.http.client.HttpClient;

public final class SecureHttpTransport {

    private static final Set<Integer> BLOCKING_STATUSES = Set.of(401, 402, 403, 407, 429, 451);

    private final WebClient webClient;
    private final UrlValidator urlValidator;
    private final ExtractionProperties properties;

    public SecureHttpTransport(UrlValidator urlValidator, PrivateAddressPolicy addressPolicy,
                               ExtractionProperties properties, int maxResponseBytes) {
        this.urlValidator = urlValidator;
        this.properties = properties;
        this.webClient = WebClient.builder()
                .clientConnector(new org.springframework.http.client.reactive.ReactorClientHttpConnector(
                        httpClient(addressPolicy, properties)))
                .exchangeStrategies(org.springframework.web.reactive.function.client.ExchangeStrategies.builder()
                        .codecs(codecs -> codecs.defaultCodecs().maxInMemorySize(maxResponseBytes))
                        .build())
                .build();
    }

    /** Redirects are followed by hand so every destination passes the same SSRF checks. */
    public FetchedPage fetch(URI url, List<MediaType> acceptedTypes, MediaType assumedType) {
        URI current = url;
        for (int hop = 0; hop <= properties.maxRedirects(); hop++) {
            urlValidator.validate(current.toString());
            Redirected redirected = attempt(current, acceptedTypes, assumedType);
            if (redirected.location() == null) {
                return new FetchedPage(current, redirected.contentType(), redirected.body());
            }
            current = current.resolve(redirected.location());
        }
        throw new ExtractionException("Exceeded " + properties.maxRedirects() + " redirects", false);
    }

    private Redirected attempt(URI url, List<MediaType> acceptedTypes, MediaType assumedType) {
        return webClient.get()
                .uri(url)
                .header(HttpHeaders.USER_AGENT, properties.userAgent())
                .accept(acceptedTypes.toArray(MediaType[]::new))
                .exchangeToMono(response -> {
                    HttpStatusCode status = response.statusCode();
                    if (status.is3xxRedirection()) {
                        String location = response.headers().header(HttpHeaders.LOCATION)
                                .stream().findFirst().orElse(null);
                        return response.releaseBody()
                                .thenReturn(new Redirected(location, null, null));
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
                    MediaType contentType = response.headers().contentType().orElse(assumedType);
                    if (contentType == null || !isReadable(contentType, acceptedTypes)) {
                        return response.releaseBody().then(reactor.core.publisher.Mono.error(
                                new UnsupportedContentException(
                                        "Cannot read " + contentType + " as a supported resource")));
                    }
                    return response.bodyToMono(byte[].class)
                            .defaultIfEmpty(new byte[0])
                            .map(body -> new Redirected(null, contentType, body));
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

    /** The connected address is checked too, closing the resolver's DNS-rebinding window. */
    private void guardAgainstRebinding(PrivateAddressPolicy addressPolicy, java.net.SocketAddress remote) {
        if (remote instanceof InetSocketAddress inet && inet.getAddress() != null
                && addressPolicy.isPrivate(inet.getAddress())) {
            throw new InvalidUrlException(
                    "Connection resolved to a non-public address: " + inet.getAddress().getHostAddress());
        }
    }

    private boolean isReadable(MediaType contentType, List<MediaType> acceptedTypes) {
        return acceptedTypes.stream().anyMatch(accepted -> accepted.includes(contentType));
    }

    private record Redirected(String location, MediaType contentType, byte[] body) {
    }
}
