package org.timmy.knowcommute.summary;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.timmy.knowcommute.bookmark.Bookmark;
import org.timmy.knowcommute.bookmark.BookmarkRepository;
import org.timmy.knowcommute.bookmark.BookmarkStatus;
import org.timmy.knowcommute.figure.EnrichedArticle;
import org.timmy.knowcommute.figure.FigureDebugService;
import org.timmy.knowcommute.figure.FigureEnricher;
import org.timmy.knowcommute.pipeline.PipelineStep;

@Slf4j
@Component
@RequiredArgsConstructor
public class SummarizationStep implements PipelineStep {

    private final SummarizationService summarizationService;
    private final ListenerBriefService briefs;
    private final BookmarkRepository bookmarks;
    private final FigureEnricher figures;
    private final FigureDebugService debug;

    @Override
    public BookmarkStatus handles() {
        return BookmarkStatus.SUMMARIZING;
    }

    @Override
    public void execute(Bookmark bookmark) {
        String extracted = bookmark.getExtractedText();
        if (extracted == null || extracted.isBlank()) {
            throw new SummarizationException("Bookmark has no extracted text to summarise");
        }

        debug.article(bookmark, extracted);
        EnrichedArticle enriched = figures.enrich(bookmark);
        String article = enriched.text();
        debug.enriched(bookmark, article);
        bookmark.useSummaryInputHash(enriched.contentHash());
        Optional<Bookmark> reusable = findReusable(bookmark)
                .filter(existing -> withinCeiling(existing, bookmark, article));
        if (reusable.isPresent()) {
            log.info("Reusing summary from bookmark {} for identical content", reusable.get().getId());
            Bookmark existing = reusable.get();
            debug.summary(bookmark, existing.getSummaryText(), existing.getId());
            bookmark.applySummary(existing.getSummaryText(), existing.getLensBrief());
            return;
        }

        ListenerBrief brief = bookmark.wantsLens() ? briefOrNull(bookmark, article) : null;
        ListenerNotes notes = brief == null ? ListenerNotes.NONE : briefs.notesFor(brief);
        String summary = summarizationService.summarise(bookmark.getTitle(), bookmark.getSourceDomain(), article,
                bookmark.getSummaryLength(), notes);
        debug.summary(bookmark, summary, null);
        bookmark.applySummary(summary, brief == null ? null : ListenerBriefService.toJson(brief));
    }

    private Optional<Bookmark> findReusable(Bookmark bookmark) {
        return bookmark.wantsLens()
                ? bookmarks.findFirstByContentHashAndSummaryLengthAndLensProfileHashAndLensBriefIsNotNullAndSummaryTextIsNotNull(
                        bookmark.getContentHash(), bookmark.getSummaryLength(), bookmark.getLensProfileHash())
                : bookmarks.findFirstByContentHashAndSummaryLengthAndLensProfileHashIsNullAndSummaryTextIsNotNull(
                        bookmark.getContentHash(), bookmark.getSummaryLength());
    }

    /**
     * Cached summaries may predate the current prompt and the ceiling gate, so a cache hit
     * gets the same check as a fresh summary; an over-long one is regenerated, not served.
     */
    private boolean withinCeiling(Bookmark existing, Bookmark bookmark, String article) {
        if (summarizationService.exceedsCeiling(existing.getSummaryText(), bookmark.getSummaryLength(), article)) {
            log.warn("Cached summary from bookmark {} is over the {} ceiling; regenerating",
                    existing.getId(), bookmark.getSummaryLength());
            return false;
        }
        return true;
    }

    /** A lens is an enhancement: when its brief cannot be had, the listener still gets a summary. */
    private ListenerBrief briefOrNull(Bookmark bookmark, String article) {
        try {
            return briefs.brief(bookmark.getLensProfile(), bookmark.getTitle(), bookmark.getSourceDomain(), article);
        } catch (RuntimeException e) {
            log.warn("Listener brief failed for bookmark {}; summarising without the lens: {}",
                    bookmark.getId(), e.getMessage());
            return null;
        }
    }
}
