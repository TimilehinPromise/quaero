package org.timmy.knowcommute.bookmark;

import java.time.Instant;
import java.util.UUID;

public record BookmarkListItem(
        UUID id,
        String url,
        String title,
        String sourceDomain,
        BookmarkStatus status,
        SummaryLength summaryLength,
        Integer wordCount,
        Integer audioDurationSec,
        Instant createdAt,
        Instant processedAt) {

    static BookmarkListItem from(Bookmark bookmark) {
        return new BookmarkListItem(
                bookmark.getId(),
                bookmark.getUrl(),
                bookmark.getTitle(),
                bookmark.getSourceDomain(),
                bookmark.getStatus(),
                bookmark.getSummaryLength(),
                bookmark.getWordCount(),
                bookmark.getAudioDurationSec(),
                bookmark.getCreatedAt(),
                bookmark.getProcessedAt());
    }
}
