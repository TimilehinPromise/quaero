package org.timmy.knowcommute.extraction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.timmy.knowcommute.extraction.LoopbackHttp.ALLOWING_LOOPBACK;
import static org.timmy.knowcommute.extraction.LoopbackHttp.INTERNAL_HOST;
import static org.timmy.knowcommute.extraction.LoopbackHttp.RESOLVER;

import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.timmy.knowcommute.bookmark.HostResolver;
import org.timmy.knowcommute.bookmark.InvalidUrlException;
import org.timmy.knowcommute.bookmark.UrlValidator;
import org.timmy.knowcommute.security.PrivateAddressPolicy;

class ArticleFetcherTest {

    private static final String ARTICLE =
            "<html><head><title>A Post</title></head><body><article><p>body</p></article></body></html>";

    private final ExtractionProperties properties = new ExtractionProperties(
            Duration.ofSeconds(5), Duration.ofSeconds(10), 5_242_880, 3, 150, "test-agent");

    private final List<DocumentExtractor> extractors =
            List.of(new ArticleExtractor(properties), new PdfExtractor(properties));

    private ArticleFetcher fetcher(PrivateAddressPolicy policy) {
        return new ArticleFetcher(new UrlValidator(RESOLVER, policy), policy, properties, extractors);
    }

    private ArticleFetcher fetcher() {
        return fetcher(ALLOWING_LOOPBACK);
    }

    private static String html(FetchedPage page) {
        return new String(page.body(), StandardCharsets.UTF_8);
    }

    @Test
    void fetchesAPage() {
        try (TestHttpServer server = TestHttpServer.start().respond("/post", 200, ARTICLE)) {
            FetchedPage page = fetcher().fetch(server.uri("/post"));

            assertThat(html(page)).contains("<article>");
            assertThat(page.finalUrl()).isEqualTo(server.uri("/post"));
            assertThat(page.contentType().includes(MediaType.TEXT_HTML)).isTrue();
        }
    }

    @Test
    void fetchesAPageLargerThanTheCodecDefaultBuffer() {
        String bigPage = "<html><body><article><p>" + "word ".repeat(100_000) + "</p></article></body></html>";
        try (TestHttpServer server = TestHttpServer.start().respond("/big", 200, bigPage)) {
            FetchedPage page = fetcher().fetch(server.uri("/big"));

            assertThat(html(page)).hasSize(bigPage.length());
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
            assertThat(html(page)).contains("<article>");
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
                    new PrivateAddressPolicy(), properties, extractors);

            assertThatThrownBy(() -> rebindable.fetch(server.uri("/post")))
                    .hasMessageContaining("non-public address");

            // The guard must fire before any request bytes are written, not after a response.
            assertThat(server.requestCount()).isZero();
        }
    }

    @Test
    void refusesAContentTypeNoExtractorCanRead() {
        try (TestHttpServer server = TestHttpServer.start()
                .respond("/clip", 200, "video/mp4", new byte[] {0, 1, 2, 3})) {
            assertThatExceptionOfType(UnsupportedContentException.class)
                    .isThrownBy(() -> fetcher().fetch(server.uri("/clip")))
                    .withMessageContaining("video/mp4");
        }
    }

    @Test
    void fetchesAPdfAndReportsItsContentType() {
        byte[] pdf = TestPdf.paper("A Paper", "A. Author");
        try (TestHttpServer server = TestHttpServer.start()
                .respond("/paper.pdf", 200, "application/pdf", pdf)) {
            FetchedPage page = fetcher().fetch(server.uri("/paper.pdf"));

            assertThat(page.contentType().includes(MediaType.APPLICATION_PDF)).isTrue();
            assertThat(page.body()).isEqualTo(pdf);
        }
    }

    @Test
    void offersEveryReadableTypeInTheAcceptHeader() {
        try (TestHttpServer server = TestHttpServer.start().respond("/post", 200, ARTICLE)) {
            fetcher().fetch(server.uri("/post"));

            assertThat(server.lastAcceptHeader())
                    .contains("text/html", "application/xhtml+xml", "application/pdf");
        }
    }

    /** Servers that omit the header are serving pages; refusing them would lose working articles. */
    @Test
    void readsAResponseWithNoContentTypeAsHtml() {
        try (TestHttpServer server = TestHttpServer.start()
                .respond("/bare", 200, null, ARTICLE.getBytes(StandardCharsets.UTF_8))) {
            FetchedPage page = fetcher().fetch(server.uri("/bare"));

            assertThat(page.contentType().includes(MediaType.TEXT_HTML)).isTrue();
        }
    }

    @Test
    void rejectsNonHttpSchemesBeforeConnecting() {
        assertThatExceptionOfType(InvalidUrlException.class)
                .isThrownBy(() -> fetcher().fetch(java.net.URI.create("file:///etc/passwd")));
    }
}
