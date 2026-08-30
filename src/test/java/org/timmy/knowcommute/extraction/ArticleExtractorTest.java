package org.timmy.knowcommute.extraction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;

class ArticleExtractorTest {

    private final ExtractionProperties properties = new ExtractionProperties(
            Duration.ofSeconds(10), Duration.ofSeconds(20), 5_242_880, 5, 150, "test-agent");

    private final ArticleExtractor extractor = new ArticleExtractor(properties);

    private FetchedPage fixture(String name, String url) {
        try (var in = new ClassPathResource("fixtures/" + name).getInputStream()) {
            return new FetchedPage(URI.create(url), MediaType.TEXT_HTML, in.readAllBytes());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static FetchedPage page(String url, String html) {
        return new FetchedPage(URI.create(url), MediaType.TEXT_HTML, html.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void extractsASubstackPost() {
        ExtractedArticle article = extractor.extract(
                fixture("substack-article.html",
                        "https://codingchallenges.substack.com/p/coding-challenge-128-mock-interview"));

        assertThat(article.title()).contains("Coding Challenge #128");
        assertThat(article.sourceDomain()).isEqualTo("codingchallenges.substack.com");
        assertThat(article.wordCount()).isGreaterThan(500);
        assertThat(article.text()).doesNotContain("<script");
    }

    @Test
    void extractsAnXLongformArticle() {
        ExtractedArticle article = extractor.extract(
                fixture("x-longform-article.html",
                        "https://x.com/lmkeev/status/2076592014322176308"));

        assertThat(article.wordCount()).isGreaterThan(1000);
        assertThat(article.text()).contains("wannabe Platos");
        assertThat(article.text()).contains("stacking reps each day");
        assertThat(article.sourceDomain()).isEqualTo("x.com");
    }

    @Test
    void skipsAPlaceholderOgTitleForTheRealHeadline() {
        ExtractedArticle article = extractor.extract(
                fixture("x-longform-article.html",
                        "https://x.com/lmkeev/status/2076592014322176308"));

        assertThat(article.title())
                .isEqualTo("How to build an audience when you hate building a \"personal brand\"");
        assertThat(article.author()).isEqualTo("@lmkeev");
    }

    @Test
    void extractsABeehiivNewsletter() {
        ExtractedArticle article = extractor.extract(
                fixture("beehiiv-newsletter.html",
                        "https://newsletter.tokenizedpod.com/p/ep-95-the-era-of-24-7-money"));

        assertThat(article.wordCount()).isGreaterThan(500);
        assertThat(article.text()).contains("Stablecard");
        assertThat(article.text()).contains("Wells Fargo");
        assertThat(article.sourceDomain()).isEqualTo("newsletter.tokenizedpod.com");
    }

    @Test
    void refusesACloudflareChallengeRatherThanSummarisingIt() {
        assertThatExceptionOfType(SiteBlockedException.class).isThrownBy(() ->
                extractor.extract(fixture("cloudflare-challenge.html",
                        "https://www.vanguardngr.com/2026/07/pension-savings-go-grassroots/")));
    }

    @Test
    void refusesAnAuthWallAsTooShortToBeAnArticle() {
        assertThatExceptionOfType(ExtractionException.class).isThrownBy(() ->
                extractor.extract(fixture("facebook-auth-wall.html",
                        "https://www.facebook.com/groups/destinationthailandvisa/permalink/2080963515759927/")));
    }

    @Test
    void stripsWwwFromTheSourceDomain() {
        ExtractedArticle article = extractor.extract(page("https://www.example.com/post",
                "<html><head><title>A Post</title></head><body><article><p>"
                        + "word ".repeat(200) + "</p></article></body></html>"));

        assertThat(article.sourceDomain()).isEqualTo("example.com");
    }

    @Test
    void prefersOpenGraphTitleOverDocumentTitle() {
        ExtractedArticle article = extractor.extract(page("https://example.com/post",
                "<html><head><title>Site Name | Real Title</title>"
                        + "<meta property=\"og:title\" content=\"Real Title\"></head>"
                        + "<body><article><p>" + "word ".repeat(200) + "</p></article></body></html>"));

        assertThat(article.title()).isEqualTo("Real Title");
    }

    @Test
    void rejectsContentBelowTheMinimumWordCount() {
        assertThatExceptionOfType(ExtractionException.class).isThrownBy(() ->
                extractor.extract(page("https://example.com/short",
                        "<html><body><article><p>Too short to narrate.</p></article></body></html>")));
    }

    @Test
    void hashesContentSoIdenticalTextIsRecognised() {
        String html = "<html><body><article><p>" + "word ".repeat(200) + "</p></article></body></html>";

        ExtractedArticle first = extractor.extract(page("https://a.example.com/x", html));
        ExtractedArticle second = extractor.extract(page("https://b.example.com/y", html));

        assertThat(first.contentHash()).isEqualTo(second.contentHash()).hasSize(64);
    }
}
