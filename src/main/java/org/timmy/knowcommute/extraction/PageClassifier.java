package org.timmy.knowcommute.extraction;

import static org.timmy.knowcommute.extraction.ArticleText.countWords;
import static org.timmy.knowcommute.extraction.ArticleText.normalise;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

/**
 * Reads a fetched page before extraction commits to a strategy, and routes it: full extraction
 * for prose-rich pages, a lowered floor for genuinely short ones, and a metadata brief or an
 * explicit refusal for pages whose content is not in the HTML at all.
 *
 * <p>The pre-scan exists because one fetch strategy cannot serve every page. Server-rendered
 * articles carry their text in the markup; client-rendered apps return an empty shell with
 * HTTP 200, and no retry of the same fetch will change those bytes. Deciding which is which
 * up front keeps thin pages from being refused by a floor meant for long reads, and keeps
 * deterministic failures from burning the retry series.
 */
@Component
@RequiredArgsConstructor
public class PageClassifier {

    private static final String CHROME_SELECTOR = "script, style, noscript, template, iframe, svg";

    private static final List<String> SHELL_MARKERS = List.of(
            "#__next", "#root", "#app", "[data-reactroot]", "[ng-app]");

    private final ExtractionProperties properties;

    public PageClassification classify(FetchedPage page) {
        Document document = HtmlParsing.parse(page);
        int bodyWords = countWords(visibleText(document));

        if (bodyWords >= properties.minimumWords()) {
            return new PageClassification(PageKind.ARTICLE, bodyWords, properties.minimumWords(),
                    false, null, null);
        }
        if (bodyWords >= properties.thinMinimumWords()) {
            return new PageClassification(PageKind.THIN, bodyWords, properties.thinMinimumWords(),
                    false, null, null);
        }
        return new PageClassification(PageKind.EMPTY, bodyWords, 0, isAppShell(document),
                briefTitle(document), briefText(document));
    }

    /**
     * An EMPTY-flavoured classification for a page already known to be unreadable directly —
     * the brief fields are filled in whatever the body's word count says. Used when extraction
     * of a page that classified as readable still comes out below its floor: the mismatch
     * between body words and content-root words that client-rendered shells produce.
     */
    public PageClassification degraded(FetchedPage page) {
        Document document = HtmlParsing.parse(page);
        return new PageClassification(PageKind.EMPTY, countWords(visibleText(document)), 0,
                isAppShell(document), briefTitle(document), briefText(document));
    }

    /** Prose a reader would see: everything except scripts, styles and embedded chrome. */
    private String visibleText(Document document) {
        Element body = document.body();
        if (body == null) {
            return "";
        }
        Element clone = body.clone();
        clone.select(CHROME_SELECTOR).remove();
        return normalise(clone.text());
    }

    /** The mount-point divs and framework attributes that betray a client-rendered app shell. */
    private boolean isAppShell(Document document) {
        return SHELL_MARKERS.stream().anyMatch(marker -> !document.select(marker).isEmpty());
    }

    /**
     * The degraded fallback for EMPTY pages: the meta description, when it is long enough to
     * carry real content. It is a stand-in for the article, never as good as the article —
     * some sites serve a generic site-wide blurb here, which no length gate can detect, so
     * this preserves whatever essence exists rather than promising the article itself.
     */
    private String briefText(Document document) {
        String description = meta(document, "meta[property=og:description]", "content");
        if (description == null) {
            description = meta(document, "meta[name=description]", "content");
        }
        return description != null && description.length() >= properties.briefMinimumChars()
                ? description : null;
    }

    private String briefTitle(Document document) {
        String title = meta(document, "meta[property=og:title]", "content");
        if (title != null) {
            return title;
        }
        Element headline = document.selectFirst("h1");
        if (headline != null && !headline.text().isBlank()) {
            return normalise(headline.text());
        }
        String documentTitle = normalise(document.title());
        return documentTitle == null || documentTitle.isBlank() ? "Untitled" : documentTitle;
    }

    private String meta(Document document, String selector, String attribute) {
        Element element = document.selectFirst(selector);
        if (element == null) {
            return null;
        }
        String value = normalise(element.attr(attribute));
        return value == null || value.isBlank() ? null : value;
    }
}
