package org.timmy.knowcommute.bookmark;

import java.time.Instant;
import java.util.UUID;

public record BookmarkDetail(
        UUID id,
        String url,
        String title,
        String author,
        String sourceDomain,
        BookmarkStatus status,
        SummaryLength summaryLength,
        String summaryText,
        Integer wordCount,
        Integer audioDurationSec,
        boolean lensRequested,
        boolean lensApplied,
        String lastError,
        Instant createdAt,
        Instant processedAt) {

    static BookmarkDetail from(Bookmark bookmark) {
        return new BookmarkDetail(
                bookmark.getId(),
                bookmark.getUrl(),
                bookmark.getTitle(),
                bookmark.getAuthor(),
                bookmark.getSourceDomain(),
                bookmark.getStatus(),
                bookmark.getSummaryLength(),
                bookmark.getSummaryText(),
                bookmark.getWordCount(),
                bookmark.getAudioDurationSec(),
                bookmark.wantsLens(),
                bookmark.isLensApplied(),
                bookmark.getLastError(),
                bookmark.getCreatedAt(),
                bookmark.getProcessedAt());
    }
}
