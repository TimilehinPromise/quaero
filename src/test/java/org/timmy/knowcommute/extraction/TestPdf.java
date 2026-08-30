package org.timmy.knowcommute.extraction;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

/**
 * Builds PDFs in memory rather than checking binaries into fixtures: the interesting cases
 * differ only in metadata and length, and a generated file makes that difference readable.
 */
final class TestPdf {

    private static final int LINES_PER_PAGE = 40;
    private static final float MARGIN = 50;
    private static final float LEADING = 16;

    private TestPdf() {
    }

    static byte[] of(String title, String author, String... lines) {
        try (PDDocument document = new PDDocument()) {
            writeLines(document, lines);
            document.getDocumentInformation().setTitle(title);
            document.getDocumentInformation().setAuthor(author);

            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            document.save(bytes);
            return bytes.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * A paper's worth of prose, so extraction clears the minimum-word gate. The heading is
     * fixed and the metadata is not, which is what lets a test pass null for either.
     */
    static byte[] paper(String title, String author) {
        String[] lines = new String[120];
        lines[0] = "A Paper.";
        for (int i = 1; i < lines.length; i++) {
            lines[i] = "Spreadsheet edits are expressed as a program the model can execute.";
        }
        return of(title, author, lines);
    }

    private static void writeLines(PDDocument document, String[] lines) throws IOException {
        PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        for (int start = 0; start < lines.length; start += LINES_PER_PAGE) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.beginText();
                content.setFont(font, 11);
                content.newLineAtOffset(MARGIN, page.getMediaBox().getHeight() - MARGIN);
                for (int i = start; i < Math.min(start + LINES_PER_PAGE, lines.length); i++) {
                    content.showText(lines[i]);
                    content.newLineAtOffset(0, -LEADING);
                }
                content.endText();
            }
        }
    }
}
