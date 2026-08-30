package org.timmy.knowcommute.extraction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.net.URI;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class PdfExtractorTest {

    private final ExtractionProperties properties = new ExtractionProperties(
            Duration.ofSeconds(10), Duration.ofSeconds(20), 5_242_880, 5, 150, "test-agent");

    private final PdfExtractor extractor = new PdfExtractor(properties);

    private static FetchedPage page(String url, byte[] pdf) {
        return new FetchedPage(URI.create(url), MediaType.APPLICATION_PDF, pdf);
    }

    @Test
    void readsTheProseOutOfAPdf() {
        ExtractedArticle article = extractor.extract(page("https://example.com/paper.pdf",
                TestPdf.paper("DAX-1: Efficient, Executable Spreadsheet Editing", "Olajide Al-Ameen")));

        assertThat(article.title()).isEqualTo("DAX-1: Efficient, Executable Spreadsheet Editing");
        assertThat(article.author()).isEqualTo("Olajide Al-Ameen");
        assertThat(article.sourceDomain()).isEqualTo("example.com");
        assertThat(article.text()).contains("Spreadsheet edits are expressed as a program");
        assertThat(article.wordCount()).isGreaterThan(150);
    }

    /** The failure this replaced: PDF internals extracted as if they were the article. */
    @Test
    void doesNotLeakPdfStructureIntoTheText() {
        ExtractedArticle article = extractor.extract(page("https://example.com/paper.pdf",
                TestPdf.paper("A Paper", "A. Author")));

        assertThat(article.text())
                .doesNotContain("/Type", "/Font", "endobj", "ReportLab", "xref");
    }

    @Test
    void fallsBackToTheOpeningLineWhenTheMetadataHasNoTitle() {
        ExtractedArticle article = extractor.extract(page("https://example.com/paper.pdf",
                TestPdf.paper(null, null)));

        assertThat(article.title()).isEqualTo("A Paper.");
        assertThat(article.author()).isNull();
    }

    @Test
    void rejectsAPdfWithTooLittleTextToNarrate() {
        assertThatExceptionOfType(ExtractionException.class).isThrownBy(() ->
                extractor.extract(page("https://example.com/cover.pdf",
                        TestPdf.of("Cover", "A. Author", "Nothing but a title page."))));
    }

    @Test
    void hashesContentSoIdenticalTextIsRecognised() {
        byte[] pdf = TestPdf.paper("A Paper", "A. Author");

        ExtractedArticle first = extractor.extract(page("https://a.example.com/x.pdf", pdf));
        ExtractedArticle second = extractor.extract(page("https://b.example.com/y.pdf", pdf));

        assertThat(first.contentHash()).isEqualTo(second.contentHash()).hasSize(64);
    }
}
