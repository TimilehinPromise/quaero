package org.timmy.knowcommute.extraction;

import static org.timmy.knowcommute.extraction.ArticleText.countWords;
import static org.timmy.knowcommute.extraction.ArticleText.normalise;
import static org.timmy.knowcommute.extraction.ArticleText.sha256;
import static org.timmy.knowcommute.extraction.ArticleText.sourceDomain;

import java.io.IOException;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

/**
 * Reads the text out of a PDF. Without this a PDF URL still fetched and still "extracted":
 * the bytes decoded into mojibake, Jsoup found no tags and handed back the file's own
 * uncompressed internals — font names, page objects, link targets — which passed the
 * minimum-word gate and got summarised as if it were prose.
 */
@Component
@RequiredArgsConstructor
public class PdfExtractor implements DocumentExtractor {

    private static final int MAX_TITLE_CHARS = 150;
    private static final int MAX_AUTHOR_CHARS = 255;

    private final ExtractionProperties properties;

    @Override
    public Set<MediaType> supportedTypes() {
        return Set.of(MediaType.APPLICATION_PDF);
    }

    @Override
    public ExtractedArticle extract(FetchedPage page) {
        try (PDDocument document = load(page.body())) {
            rejectIfExtractionForbidden(document);

            String text = normalise(new PDFTextStripper().getText(document));
            int wordCount = countWords(text);
            if (wordCount < properties.minimumWords()) {
                throw new ExtractionException(
                        "Extracted only " + wordCount + " words from the PDF, below the "
                                + properties.minimumWords() + "-word minimum");
            }

            PDDocumentInformation info = document.getDocumentInformation();
            return new ExtractedArticle(
                    title(info, text), author(info), sourceDomain(page.finalUrl()),
                    text, wordCount, sha256(text));
        } catch (IOException e) {
            throw new ExtractionException("Could not read the PDF", e);
        }
    }

    private PDDocument load(byte[] body) throws IOException {
        try {
            return Loader.loadPDF(body);
        } catch (InvalidPasswordException e) {
            throw new UnsupportedContentException("The PDF is password-protected");
        }
    }

    /**
     * An owner password can permit opening while forbidding text extraction. PDFBox enforces
     * this inside the stripper, but as a plain IOException that would look retryable; it is not.
     */
    private void rejectIfExtractionForbidden(PDDocument document) {
        if (!document.getCurrentAccessPermission().canExtractContent()) {
            throw new UnsupportedContentException("The PDF forbids text extraction");
        }
    }

    /** Falls back to the opening line, which in a paper or report is usually the title. */
    private String title(PDDocumentInformation info, String text) {
        String declared = normalise(info.getTitle());
        if (declared != null && !declared.isBlank()) {
            return clamp(declared, MAX_TITLE_CHARS);
        }
        String opening = text.split("(?<=[.!?])\\s+", 2)[0];
        return opening.isBlank() ? "Untitled" : clamp(opening, MAX_TITLE_CHARS);
    }

    private String author(PDDocumentInformation info) {
        String declared = normalise(info.getAuthor());
        return declared == null || declared.isBlank() ? null : clamp(declared, MAX_AUTHOR_CHARS);
    }

    /** PDF metadata is unvalidated and occasionally enormous; the columns behind it are not. */
    private String clamp(String value, int maxChars) {
        return value.length() <= maxChars ? value : value.substring(0, maxChars).trim();
    }
}
