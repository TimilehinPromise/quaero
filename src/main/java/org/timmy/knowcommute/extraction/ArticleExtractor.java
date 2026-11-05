package org.timmy.knowcommute.extraction;

import static org.timmy.knowcommute.extraction.ArticleText.countWords;
import static org.timmy.knowcommute.extraction.ArticleText.normalise;
import static org.timmy.knowcommute.extraction.ArticleText.sha256;
import static org.timmy.knowcommute.extraction.ArticleText.sourceDomain;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.timmy.knowcommute.figure.FigureProperties;

@Component
@RequiredArgsConstructor
public class ArticleExtractor implements DocumentExtractor {

    private static final List<String> CONTENT_SELECTORS = List.of(
            "div.x-article-body", "article", "[role=main]", "main", "div.post-content",
            "div.entry-content", "div.article-body", "#content-blocks");

    private static final String CHROME_SELECTOR =
            "script, style, noscript, nav, aside, footer, header, form, iframe, svg,"
                    + " .advertisement, .ad, .share, .related, .comments, .newsletter,"
                    + " [class*=related], [id*=related], [class*=recommended], [id*=recommended],"
                    + " [class*=read-next], [class*=more-like], [class*=sponsored],"
                    + " [data-testid*=related], [data-testid*=recommended]";

    /**
     * Section labels that mark recommendation rails. Matched against an element's own text, not
     * its subtree, so prose that merely mentions "related articles" is never pruned.
     */
    private static final java.util.regex.Pattern RELATED_HEADING =
            java.util.regex.Pattern.compile("related (articles|posts|stories|news)|"
                    + "you may also (like|enjoy)|you might also like|"
                    + "recommended (for you|reading|articles?)|suggested (reading|articles?)|"
                    + "more from .+|read next|what to read next|sponsored content|partner content");

    private static final int MAX_HEADING_LABEL_CHARS = 40;

    /** A related-content section bigger than half the article is the article; never remove it. */
    private static final double MAX_PRUNE_FRACTION = 0.5;

    private static final List<String> CHALLENGE_MARKERS = List.of(
            "just a moment", "checking your browser", "enable javascript and cookies",
            "attention required", "verifying you are human", "cf-browser-verification");

    private static final int MIN_BLOCK_WORDS = 25;
    private static final double MAX_LINK_DENSITY = 0.5;
    private static final int MAX_TITLE_CHARS = 150;

    private static final java.util.regex.Pattern PLACEHOLDER_TITLE =
            java.util.regex.Pattern.compile("^.*\\(@\\w+\\) on X$|^.* on X: .*$");

    private final ExtractionProperties properties;
    private final FigureProperties figureProperties;

    @Override
    public Set<MediaType> supportedTypes() {
        return Set.of(MediaType.TEXT_HTML, MediaType.APPLICATION_XHTML_XML);
    }

    @Override
    public ExtractedArticle extract(FetchedPage page) {
        return extract(page, properties.minimumWords());
    }

    /**
     * The floor is a parameter, not always the configured minimum: {@link PageClassifier}
     * routes genuinely short pages here with a lower one so they are read, not refused.
     */
    public ExtractedArticle extract(FetchedPage page, int minimumWords) {
        Document document = HtmlParsing.parse(page);
        rejectIfChallengePage(document);

        Element content = selectContent(document);
        content.select(CHROME_SELECTOR).remove();
        pruneRelatedSections(content);

        String articleTitle = title(document);
        FigureMarkup.Result figures = FigureMarkup.html(content, articleTitle, figureProperties);
        if (figures.wordCount() < minimumWords) {
            throw new ExtractionException(
                    "Extracted only " + figures.wordCount() + " words, below the "
                            + minimumWords + "-word minimum", false);
        }

        return new ExtractedArticle(articleTitle, author(document), sourceDomain(page.finalUrl()),
                figures.text(), figures.wordCount(), sha256(figures.text()), figures.figures(), figures.decisions());
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
     * Recommendation rails ("Related Articles" cards and the like) often live inside the chosen
     * content root, past the end of the real article. They survive the chrome pass because their
     * classes are hashed or unmarked, and their images then pollute figure selection. The rail is
     * almost always introduced by a short standalone heading, so that label is the reliable
     * handle: find it, then remove the section or region that contains it.
     */
    private void pruneRelatedSections(Element content) {
        int contentWords = countWords(content.text());
        List<Element> removals = new ArrayList<>();
        for (Element element : content.getAllElements()) {
            if (element == content) {
                continue;
            }
            String label = normalise(element.ownText());
            if (label == null || label.length() > MAX_HEADING_LABEL_CHARS
                    || !RELATED_HEADING.matcher(label.toLowerCase(Locale.ROOT)).matches()) {
                continue;
            }
            Element target = element.closest("section, [role=region]");
            if (target != null && target != content && !removals.contains(target)
                    && countWords(target.text()) <= contentWords * MAX_PRUNE_FRACTION) {
                removals.add(target);
            }
        }
        removals.forEach(Element::remove);
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
        if (!ogTitle.isBlank() && !PLACEHOLDER_TITLE.matcher(ogTitle.trim()).matches()) {
            return normalise(ogTitle);
        }
        String description = document.select("meta[property=og:description]").attr("content");
        if (!description.isBlank() && description.trim().length() <= MAX_TITLE_CHARS) {
            return normalise(description);
        }
        Element headline = document.selectFirst("h1");
        if (headline != null && !headline.text().isBlank()) {
            return normalise(headline.text());
        }
        String title = document.title();
        return title.isBlank() ? "Untitled" : normalise(title);
    }

    private String author(Document document) {
        String creator = document.select("meta[name=twitter:creator]").attr("content");
        if (!creator.isBlank()) {
            return normalise(creator);
        }
        String metaAuthor = document.select("meta[name=author]").attr("content");
        if (!metaAuthor.isBlank()) {
            return normalise(metaAuthor);
        }
        String articleAuthor = document.select("meta[property=article:author]").attr("content");
        return articleAuthor.isBlank() ? null : normalise(articleAuthor);
    }
}
