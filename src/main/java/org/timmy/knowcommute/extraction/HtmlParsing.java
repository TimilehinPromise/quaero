package org.timmy.knowcommute.extraction;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

/** Shared lenient HTML parsing for the extractor and the classifier, so both see the same DOM. */
final class HtmlParsing {

    private HtmlParsing() {
    }

    /**
     * The declared charset is a hint, not the answer. Jsoup re-reads it from the meta tag or
     * the BOM when the header omits it or gets it wrong, which older sites regularly do.
     */
    static Document parse(FetchedPage page) {
        Charset declared = page.contentType() == null ? null : page.contentType().getCharset();
        try {
            return Jsoup.parse(new ByteArrayInputStream(page.body()),
                    declared == null ? null : declared.name(), page.finalUrl().toString());
        } catch (IOException e) {
            throw new ExtractionException("Could not read the page as HTML", e, false);
        }
    }
}
