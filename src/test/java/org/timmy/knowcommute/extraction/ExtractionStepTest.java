package org.timmy.knowcommute.extraction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.timmy.knowcommute.extraction.LoopbackHttp.ALLOWING_LOOPBACK;
import static org.timmy.knowcommute.extraction.LoopbackHttp.RESOLVER;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.timmy.knowcommute.bookmark.Bookmark;
import org.timmy.knowcommute.bookmark.BookmarkStatus;
import org.timmy.knowcommute.bookmark.SummaryLength;
import org.timmy.knowcommute.bookmark.UrlValidator;

/** The content type, not the file extension, decides which extractor runs. */
class ExtractionStepTest {

    private static final String ARTICLE = "<html><head><title>A Post</title></head><body><article><p>"
            + "word ".repeat(200) + "</p></article></body></html>";

    private final ExtractionProperties properties = new ExtractionProperties(
            Duration.ofSeconds(5), Duration.ofSeconds(10), 5_242_880, 3, 150, "test-agent");

    private final List<DocumentExtractor> extractors =
            List.of(new ArticleExtractor(properties), new PdfExtractor(properties));

    private final ExtractionStep step = new ExtractionStep(
            new ArticleFetcher(new UrlValidator(RESOLVER, ALLOWING_LOOPBACK), ALLOWING_LOOPBACK,
                    properties, extractors),
            extractors);

    @Test
    void routesAPdfToThePdfExtractor() {
        try (TestHttpServer server = TestHttpServer.start().respond(
                "/paper.pdf", 200, "application/pdf", TestPdf.paper("A Paper", "A. Author"))) {
            Bookmark bookmark = bookmarkFor(server, "/paper.pdf");

            step.execute(bookmark);

            assertThat(bookmark.getTitle()).isEqualTo("A Paper");
            assertThat(bookmark.getAuthor()).isEqualTo("A. Author");
            assertThat(bookmark.getExtractedText())
                    .contains("Spreadsheet edits are expressed as a program")
                    .doesNotContain("endobj");
            assertThat(bookmark.getStatus()).isEqualTo(BookmarkStatus.SUMMARIZING);
        }
    }

    @Test
    void routesHtmlToTheArticleExtractor() {
        try (TestHttpServer server = TestHttpServer.start().respond("/post", 200, ARTICLE)) {
            Bookmark bookmark = bookmarkFor(server, "/post");

            step.execute(bookmark);

            assertThat(bookmark.getTitle()).isEqualTo("A Post");
            assertThat(bookmark.getStatus()).isEqualTo(BookmarkStatus.SUMMARIZING);
        }
    }

    /** A .pdf in the path means nothing if the server sends something else. */
    @Test
    void refusesADocumentNoExtractorHandles() {
        try (TestHttpServer server = TestHttpServer.start().respond(
                "/paper.pdf", 200, "application/zip", "not a pdf".getBytes(StandardCharsets.UTF_8))) {
            Bookmark bookmark = bookmarkFor(server, "/paper.pdf");

            assertThatExceptionOfType(UnsupportedContentException.class)
                    .isThrownBy(() -> step.execute(bookmark));
            assertThat(bookmark.getStatus()).isEqualTo(BookmarkStatus.PENDING);
        }
    }

    private Bookmark bookmarkFor(TestHttpServer server, String path) {
        return Bookmark.pending(null, server.uri(path).toString(), SummaryLength.LONG);
    }
}
