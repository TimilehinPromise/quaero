package org.timmy.knowcommute.bookmark;

import java.time.Instant;
import java.util.UUID;

public record BookmarkDetail(
        UUID id,
        String url,
        String title,
        String sourceDomain,
        BookmarkStatus status,
        SummaryLength summaryLength,
        String summaryText,
        Integer wordCount,
        Integer audioDurationSec,
        String lastError,
        Instant createdAt,
        Instant processedAt) {

    static BookmarkDetail from(Bookmark bookmark) {
        return new BookmarkDetail(
                bookmark.getId(),
                bookmark.getUrl(),
                bookmark.getTitle(),
                bookmark.getSourceDomain(),
                bookmark.getStatus(),
                bookmark.getSummaryLength(),
                bookmark.getSummaryText(),
                bookmark.getWordCount(),
                bookmark.getAudioDurationSec(),
                bookmark.getLastError(),
                bookmark.getCreatedAt(),
                bookmark.getProcessedAt());
    }
}
