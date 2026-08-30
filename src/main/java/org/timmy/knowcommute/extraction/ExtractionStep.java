package org.timmy.knowcommute.extraction;

import java.net.URI;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.timmy.knowcommute.bookmark.Bookmark;
import org.timmy.knowcommute.bookmark.BookmarkStatus;
import org.timmy.knowcommute.pipeline.PipelineStep;

@Component
@RequiredArgsConstructor
public class ExtractionStep implements PipelineStep {

    private final ArticleFetcher fetcher;
    private final List<DocumentExtractor> extractors;

    @Override
    public BookmarkStatus handles() {
        return BookmarkStatus.PENDING;
    }

    @Override
    public void execute(Bookmark bookmark) {
        FetchedPage page = fetcher.fetch(URI.create(bookmark.getUrl()));
        ExtractedArticle article = extractorFor(page.contentType()).extract(page);
        bookmark.applyExtraction(article.title(), article.author(), article.sourceDomain(), article.text(),
                article.wordCount(), article.contentHash());
    }

    private DocumentExtractor extractorFor(MediaType contentType) {
        return extractors.stream()
                .filter(extractor -> extractor.supports(contentType))
                .findFirst()
                .orElseThrow(() -> new UnsupportedContentException(
                        "No extractor handles " + contentType));
    }
}
