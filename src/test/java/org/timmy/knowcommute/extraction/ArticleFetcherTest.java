package org.timmy.knowcommute.extraction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.InetAddress;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.timmy.knowcommute.bookmark.HostResolver;
import org.timmy.knowcommute.bookmark.InvalidUrlException;
import org.timmy.knowcommute.bookmark.UrlValidator;
import org.timmy.knowcommute.security.PrivateAddressPolicy;

class ArticleFetcherTest {

    private static final String INTERNAL_HOST = "internal.example";
    private static final String ARTICLE =
            "<html><head><title>A Post</title></head><body><article><p>body</p></article></body></html>";

    private final ExtractionProperties properties = new ExtractionProperties(
            Duration.ofSeconds(5), Duration.ofSeconds(10), 5_242_880, 3, 150, "test-agent");

    /** Real policy in every respect except loopback, so the test server is reachable. */
    private static final PrivateAddressPolicy ALLOWING_LOOPBACK = new PrivateAddressPolicy() {
        @Override
        public boolean isPrivate(InetAddress address) {
            return !address.isLoopbackAddress() && super.isPrivate(address);
        }
    };

    /** Resolves the test server to loopback and one named host into the private range. */
    private static final HostResolver RESOLVER = host -> INTERNAL_HOST.equals(host)
            ? new InetAddress[] {InetAddress.getByName("10.0.0.1")}
            : new InetAddress[] {InetAddress.getByName(host)};

    private ArticleFetcher fetcher(PrivateAddressPolicy policy) {
        return new ArticleFetcher(new UrlValidator(RESOLVER, policy), policy, properties);
    }

    private ArticleFetcher fetcher() {
        return fetcher(ALLOWING_LOOPBACK);
    }

    @Test
    void fetchesAPage() {
        try (TestHttpServer server = TestHttpServer.start().respond("/post", 200, ARTICLE)) {
            FetchedPage page = fetcher().fetch(server.uri("/post"));

            assertThat(page.html()).contains("<article>");
            assertThat(page.finalUrl()).isEqualTo(server.uri("/post"));
        }
    }

    @Test
    void treatsForbiddenAsBlockedRatherThanRetryable() {
        try (TestHttpServer server = TestHttpServer.start()
                .respond("/blocked", 403, "Just a moment...")) {
            assertThatExceptionOfType(SiteBlockedException.class)
                    .isThrownBy(() -> fetcher().fetch(server.uri("/blocked")))
                    .withMessageContaining("403");
        }
    }

    @Test
    void treatsRateLimitingAsBlocked() {
        try (TestHttpServer server = TestHttpServer.start().respond("/limited", 429, "slow down")) {
            assertThatExceptionOfType(SiteBlockedException.class)
                    .isThrownBy(() -> fetcher().fetch(server.uri("/limited")));
        }
    }

    @Test
    void treatsServerErrorsAsRetryableRatherThanBlocked() {
        try (TestHttpServer server = TestHttpServer.start().respond("/broken", 503, "oops")) {
            assertThatExceptionOfType(ExtractionException.class)
                    .isThrownBy(() -> fetcher().fetch(server.uri("/broken")))
                    .withMessageContaining("503");
        }
    }

    @Test
    void followsRedirectsAndReportsTheFinalUrl() {
        try (TestHttpServer server = TestHttpServer.start()) {
            server.redirect("/start", "/middle").redirect("/middle", "/end")
                    .respond("/end", 200, ARTICLE);

            FetchedPage page = fetcher().fetch(server.uri("/start"));

            assertThat(page.finalUrl()).isEqualTo(server.uri("/end"));
            assertThat(page.html()).contains("<article>");
            assertThat(server.requestCount()).isEqualTo(3);
        }
    }

    @Test
    void refusesARedirectIntoThePrivateNetwork() {
        try (TestHttpServer server = TestHttpServer.start()) {
            server.redirect("/start", "http://" + INTERNAL_HOST + "/admin");

            assertThatExceptionOfType(InvalidUrlException.class)
                    .isThrownBy(() -> fetcher().fetch(server.uri("/start")))
                    .withMessageContaining("non-public address");

            assertThat(server.requestCount()).isEqualTo(1);
        }
    }

    @Test
    void stopsAfterTheRedirectLimit() {
        try (TestHttpServer server = TestHttpServer.start()) {
            server.redirect("/loop", "/loop");

            assertThatExceptionOfType(ExtractionException.class)
                    .isThrownBy(() -> fetcher().fetch(server.uri("/loop")))
                    .withMessageContaining("redirects");
        }
    }

    @Test
    void refusesToConnectToAPrivateAddressEvenWhenTheUrlLooksPublic() {
        try (TestHttpServer server = TestHttpServer.start().respond("/post", 200, ARTICLE)) {
            HostResolver lyingResolver = host -> new InetAddress[] {InetAddress.getByName("93.184.216.34")};
            ArticleFetcher rebindable = new ArticleFetcher(
                    new UrlValidator(lyingResolver, new PrivateAddressPolicy()),
                    new PrivateAddressPolicy(), properties);

            assertThatThrownBy(() -> rebindable.fetch(server.uri("/post")))
                    .hasMessageContaining("non-public address");

            // The guard must fire before any request bytes are written, not after a response.
            assertThat(server.requestCount()).isZero();
        }
    }

    @Test
    void rejectsNonHttpSchemesBeforeConnecting() {
        assertThatExceptionOfType(InvalidUrlException.class)
                .isThrownBy(() -> fetcher().fetch(java.net.URI.create("file:///etc/passwd")));
    }
}
