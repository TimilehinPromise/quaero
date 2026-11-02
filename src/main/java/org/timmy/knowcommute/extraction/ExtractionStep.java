package org.timmy.knowcommute.extraction;

import static org.timmy.knowcommute.extraction.ArticleText.countWords;
import static org.timmy.knowcommute.extraction.ArticleText.sha256;
import static org.timmy.knowcommute.extraction.ArticleText.sourceDomain;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.timmy.knowcommute.bookmark.Bookmark;
import org.timmy.knowcommute.bookmark.BookmarkStatus;
import org.timmy.knowcommute.figure.FigureProperties;
import org.timmy.knowcommute.figure.FigureStore;
import org.timmy.knowcommute.pipeline.PipelineStep;

@Slf4j
@Component
public class ExtractionStep implements PipelineStep {

    private final ArticleFetcher fetcher;
    private final List<DocumentExtractor> extractors;
    private final PageClassifier classifier;
    private final ExtractionProperties extractionProperties;
    private final FigureProperties figureProperties;
    private final FigureStore figureStore;

    /** Null when readcast.reader.enabled=false; EMPTY pages then degrade straight to the brief. */
    private final PageReader reader;

    @Autowired
    public ExtractionStep(ArticleFetcher fetcher, List<DocumentExtractor> extractors,
                          PageClassifier classifier, ExtractionProperties extractionProperties,
                          FigureProperties figureProperties, FigureStore figureStore,
                          ReaderProperties readerProperties, ObjectProvider<PageReader> readers) {
        this(fetcher, extractors, classifier, extractionProperties, figureProperties, figureStore,
                resolveReader(readerProperties, readers));
    }

    /** Test constructor with the reader supplied directly; null disables the fallback. */
    ExtractionStep(ArticleFetcher fetcher, List<DocumentExtractor> extractors,
                   PageClassifier classifier, ExtractionProperties extractionProperties,
                   FigureProperties figureProperties, FigureStore figureStore, PageReader reader) {
        this.fetcher = fetcher;
        this.extractors = extractors;
        this.classifier = classifier;
        this.extractionProperties = extractionProperties;
        this.figureProperties = figureProperties;
        this.figureStore = figureStore;
        this.reader = reader;
    }

    private static PageReader resolveReader(ReaderProperties properties, ObjectProvider<PageReader> readers) {
        if (!properties.enabled()) {
            return null;
        }
        return readers.stream()
                .filter(candidate -> candidate.provider().equals(properties.provider()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "readcast.reader.enabled=true but no PageReader implements provider '"
                                + properties.provider() + "'; available: "
                                + readers.stream().map(PageReader::provider).toList()));
    }

    @Override
    public BookmarkStatus handles() {
        return BookmarkStatus.PENDING;
    }

    @Override
    public void execute(Bookmark bookmark) {
        FetchedPage page = fetcher.fetch(URI.create(bookmark.getUrl()));
        DocumentExtractor extractor = extractorFor(page.contentType());
        ExtractedArticle article = extractor instanceof ArticleExtractor articleExtractor
                ? extractHtml(articleExtractor, page)
                : extractor.extract(page);
        bookmark.applyExtraction(article.title(), article.author(), article.sourceDomain(), article.text(),
                article.wordCount(), article.contentHash());
        figureStore.replace(bookmark, article.figures());
    }

    /**
     * HTML pages are classified before extraction so the route fits the page: the normal
     * floor for prose-rich pages, a lowered one for genuinely short pages, and for pages
     * whose content lives behind JavaScript, a hosted reader — then a metadata brief, then
     * an explicit, non-retryable refusal.
     */
    private ExtractedArticle extractHtml(ArticleExtractor extractor, FetchedPage page) {
        PageClassification classification = classifier.classify(page);
        return switch (classification.kind()) {
            case ARTICLE, THIN -> extractOrDegrade(extractor, page, classification);
            case EMPTY -> emptyPage(extractor, page, classification);
        };
    }

    /**
     * A page can classify as readable on body words yet extract below its floor: navigation
     * and footer chrome push the body count up while the content root of a client-rendered
     * shell holds a dozen words. Rather than fail the bookmark, render it — the hosted reader
     * sees the JavaScript the plain fetch cannot run — and degrade to the metadata brief when
     * rendering is unavailable.
     */
    private ExtractedArticle extractOrDegrade(ArticleExtractor extractor, FetchedPage page,
                                              PageClassification classification) {
        try {
            return extractor.extract(page, classification.minimumWords());
        } catch (ExtractionException failure) {
            if (failure.isRetryable()) {
                throw failure;
            }
            return emptyPage(extractor, page, classifier.degraded(page));
        }
    }

    /**
     * EMPTY pages may only exist after JavaScript runs. The hosted reader renders them
     * remotely and returns the content; whatever it cannot render degrades to the metadata
     * brief. Reader service errors propagate — a rate limit or outage should retry or fail
     * loudly, not silently downgrade every page to a blurb.
     */
    private ExtractedArticle emptyPage(ArticleExtractor extractor, FetchedPage page,
                                       PageClassification classification) {
        if (reader != null) {
            RenderedPage rendered = reader.read(page.finalUrl());
            if (rendered != null) {
                ExtractedArticle article = fromRendered(extractor, page, classification, rendered);
                if (article != null) {
                    return article;
                }
                log.debug("Hosted reader returned no usable content for {}; falling back to the metadata brief",
                        page.finalUrl());
            }
        }
        return metadataBrief(page, classification);
    }

    /** Reader HTML re-enters the normal pipeline; Markdown receives the same figure markup rules. */
    private ExtractedArticle fromRendered(ArticleExtractor extractor, FetchedPage page,
                                          PageClassification classification, RenderedPage rendered) {
        if (rendered.html() != null) {
            FetchedPage renderedPage = new FetchedPage(page.finalUrl(), MediaType.TEXT_HTML,
                    rendered.html().getBytes(StandardCharsets.UTF_8));
            PageClassification secondLook = classifier.classify(renderedPage);
            if (secondLook.kind() != PageKind.EMPTY) {
                return extractor.extract(renderedPage, secondLook.minimumWords());
            }
            return null;
        }
        String title = rendered.title() != null ? rendered.title() : classification.briefTitle();
        FigureMarkup.Result figures = FigureMarkup.markdown(
                rendered.text(), page.finalUrl(), title, figureProperties);
        if (figures.wordCount() < extractionProperties.thinMinimumWords()) {
            return null;
        }
        return new ExtractedArticle(title, null, sourceDomain(page.finalUrl()),
                figures.text(), figures.wordCount(), sha256(figures.text()), figures.figures());
    }

    /**
     * The degraded route for EMPTY pages: narrate the meta description as the briefest
     * stand-in for an article that is not in the markup. Pages without a usable description
     * are refused outright; the pipeline turns that into BLOCKED and the paste-text path.
     */
    private ExtractedArticle metadataBrief(FetchedPage page, PageClassification classification) {
        if (classification.briefText() == null) {
            throw new NoReadableContentException(classification.javaScriptRendered()
                    ? "This page renders its content with JavaScript, so a server-side fetch "
                            + "finds no article text — paste the text to continue"
                    : "This page has no readable article text — paste the text to continue");
        }
        String text = classification.briefTitle().equals("Untitled")
                ? classification.briefText()
                : classification.briefTitle() + ". " + classification.briefText();
        return new ExtractedArticle(classification.briefTitle(), null, sourceDomain(page.finalUrl()),
                text, countWords(text), sha256(text));
    }

    private DocumentExtractor extractorFor(MediaType contentType) {
        return extractors.stream()
                .filter(extractor -> extractor.supports(contentType))
                .findFirst()
                .orElseThrow(() -> new UnsupportedContentException(
                        "No extractor handles " + contentType));
    }
}
