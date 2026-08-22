package org.timmy.knowcommute.extraction;

import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.timmy.knowcommute.bookmark.Bookmark;
import org.timmy.knowcommute.bookmark.BookmarkStatus;
import org.timmy.knowcommute.pipeline.PipelineStep;

@Component
@RequiredArgsConstructor
public class ExtractionStep implements PipelineStep {

    private final ArticleFetcher fetcher;
    private final ArticleExtractor extractor;

    @Override
    public BookmarkStatus handles() {
        return BookmarkStatus.PENDING;
    }

    @Override
    public void execute(Bookmark bookmark) {
        FetchedPage page = fetcher.fetch(URI.create(bookmark.getUrl()));
        ExtractedArticle article = extractor.extract(page);
        bookmark.applyExtraction(article.title(), article.sourceDomain(), article.text(),
                article.wordCount(), article.contentHash());
    }
}
