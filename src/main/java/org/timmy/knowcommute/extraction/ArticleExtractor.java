package org.timmy.knowcommute.extraction;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ArticleExtractor {

    private static final List<String> CONTENT_SELECTORS = List.of(
            "div.x-article-body", "article", "[role=main]", "main", "div.post-content",
            "div.entry-content", "div.article-body");

    private static final String CHROME_SELECTOR =
            "script, style, noscript, nav, aside, footer, header, form, iframe, svg,"
                    + " .advertisement, .ad, .share, .related, .comments, .newsletter";

    private static final List<String> CHALLENGE_MARKERS = List.of(
            "just a moment", "checking your browser", "enable javascript and cookies",
            "attention required", "verifying you are human", "cf-browser-verification");

    private static final int MIN_BLOCK_WORDS = 25;
    private static final double MAX_LINK_DENSITY = 0.5;

    private final ExtractionProperties properties;

    public ExtractedArticle extract(FetchedPage page) {
        Document document = Jsoup.parse(page.html(), page.finalUrl().toString());
        rejectIfChallengePage(document);

        Element content = selectContent(document);
        content.select(CHROME_SELECTOR).remove();

        String text = normalise(content.text());
        int wordCount = countWords(text);
        if (wordCount < properties.minimumWords()) {
            throw new ExtractionException(
                    "Extracted only " + wordCount + " words, below the "
                            + properties.minimumWords() + "-word minimum");
        }

        return new ExtractedArticle(
                title(document), sourceDomain(page.finalUrl()), text, wordCount, sha256(text));
    }

    /**
     * A bot challenge returns HTTP 200 with a page that extracts cleanly to a few words. Left
     * unchecked it would be summarised and billed for, so it is refused before anything else.
     */
    private void rejectIfChallengePage(Document document) {
        String haystack = (document.title() + " " + document.body().text())
                .toLowerCase(Locale.ROOT);
        boolean looksLikeChallenge = CHALLENGE_MARKERS.stream().anyMatch(haystack::contains);
        if (looksLikeChallenge && countWords(document.body().text()) < 200) {
            throw new SiteBlockedException("Site returned a bot challenge page");
        }
    }

    private Element selectContent(Document document) {
        for (String selector : CONTENT_SELECTORS) {
            Elements matches = document.select(selector);
            if (!matches.isEmpty() && countWords(matches.first().text()) >= MIN_BLOCK_WORDS) {
                return matches.first();
            }
        }
        return densestBlock(document);
    }

    /**
     * Fallback when no semantic container matches: pick the block with the most text that is
     * not mostly links. Link density is what separates prose from navigation and link farms.
     */
    private Element densestBlock(Document document) {
        Element best = document.body();
        int bestWords = 0;
        for (Element candidate : document.body().select("div, section, td")) {
            int words = countWords(candidate.ownText()) + directParagraphWords(candidate);
            if (words > bestWords && linkDensity(candidate) < MAX_LINK_DENSITY) {
                best = candidate;
                bestWords = words;
            }
        }
        return best;
    }

    private int directParagraphWords(Element element) {
        return element.children().stream()
                .filter(child -> "p".equals(child.tagName()))
                .mapToInt(child -> countWords(child.text()))
                .sum();
    }

    private double linkDensity(Element element) {
        int total = countWords(element.text());
        if (total == 0) {
            return 1.0;
        }
        int linked = element.select("a").stream().mapToInt(a -> countWords(a.text())).sum();
        return (double) linked / total;
    }

    private String title(Document document) {
        String ogTitle = document.select("meta[property=og:title]").attr("content");
        String title = !ogTitle.isBlank() ? ogTitle : document.title();
        return title.isBlank() ? "Untitled" : normalise(title);
    }

    private String sourceDomain(URI url) {
        String host = url.getHost();
        return host == null ? "" : host.replaceFirst("^www\\.", "");
    }

    private String normalise(String text) {
        return text.replaceAll("\\s+", " ").trim();
    }

    private int countWords(String text) {
        String trimmed = text.trim();
        return trimmed.isEmpty() ? 0 : trimmed.split("\\s+").length;
    }

    private String sha256(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by the JDK", e);
        }
    }
}
