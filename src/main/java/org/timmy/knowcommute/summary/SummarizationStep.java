package org.timmy.knowcommute.summary;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.timmy.knowcommute.bookmark.Bookmark;
import org.timmy.knowcommute.bookmark.BookmarkRepository;
import org.timmy.knowcommute.bookmark.BookmarkStatus;
import org.timmy.knowcommute.pipeline.PipelineStep;

@Slf4j
@Component
@RequiredArgsConstructor
public class SummarizationStep implements PipelineStep {

    private final SummarizationService summarizationService;
    private final BookmarkRepository bookmarks;

    @Override
    public BookmarkStatus handles() {
        return BookmarkStatus.SUMMARIZING;
    }

    @Override
    public void execute(Bookmark bookmark) {
        String article = bookmark.getExtractedText();
        if (article == null || article.isBlank()) {
            throw new SummarizationException("Bookmark has no extracted text to summarise");
        }

        bookmark.applySummary(reuseOrGenerate(bookmark, article));
    }

    private String reuseOrGenerate(Bookmark bookmark, String article) {
        return bookmarks.findFirstByContentHashAndSummaryLengthAndSummaryTextIsNotNull(
                        bookmark.getContentHash(), bookmark.getSummaryLength())
                .map(existing -> reuseWithinCeiling(existing, bookmark, article))
                .orElseGet(() -> summarizationService.summarise(
                        bookmark.getTitle(), bookmark.getSourceDomain(), article,
                        bookmark.getSummaryLength()));
    }

    /**
     * Cached summaries may predate the current prompt and the ceiling gate, so a cache hit
     * gets the same check as a fresh summary; an over-long one is regenerated, not served.
     */
    private String reuseWithinCeiling(Bookmark existing, Bookmark bookmark, String article) {
        String cached = existing.getSummaryText();
        if (summarizationService.exceedsCeiling(cached, bookmark.getSummaryLength(), article)) {
            log.warn("Cached summary from bookmark {} is over the {} ceiling; regenerating",
                    existing.getId(), bookmark.getSummaryLength());
            return summarizationService.summarise(
                    bookmark.getTitle(), bookmark.getSourceDomain(), article,
                    bookmark.getSummaryLength());
        }
        log.info("Reusing summary from bookmark {} for identical content", existing.getId());
        return cached;
    }
}
