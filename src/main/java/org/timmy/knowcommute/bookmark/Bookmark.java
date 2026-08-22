package org.timmy.knowcommute.bookmark;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.timmy.knowcommute.support.Auditable;
import org.timmy.knowcommute.user.DeviceUser;

@Entity
@Getter
@Table(name = "bookmarks")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Bookmark extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private DeviceUser user;

    @Column(name = "user_id", insertable = false, updatable = false)
    private UUID userId;

    @Column(nullable = false, columnDefinition = "text")
    private String url;

    @Column(length = 512)
    private String title;

    @Column(name = "source_domain", length = 255)
    private String sourceDomain;

    @Column(name = "extracted_text", columnDefinition = "text")
    private String extractedText;

    @Column(name = "summary_text", columnDefinition = "text")
    private String summaryText;

    @Column(name = "content_hash", length = 64)
    private String contentHash;

    @Column(name = "word_count")
    private Integer wordCount;

    @Column(name = "audio_storage_key", length = 512)
    private String audioStorageKey;

    @Column(name = "audio_duration_sec")
    private Integer audioDurationSec;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private BookmarkStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "summary_length", nullable = false, length = 16)
    private SummaryLength summaryLength;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "last_error", columnDefinition = "text")
    private String lastError;

    @Column(name = "next_attempt_at")
    private Instant nextAttemptAt;

    @Column(name = "processed_at")
    private Instant processedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    private Bookmark(DeviceUser user, String url, SummaryLength summaryLength) {
        this.user = user;
        this.url = url;
        this.summaryLength = summaryLength;
        this.status = BookmarkStatus.PENDING;
    }

    public static Bookmark pending(DeviceUser user, String url, SummaryLength summaryLength) {
        return new Bookmark(user, url, summaryLength);
    }

    public void applyExtraction(String title, String sourceDomain, String extractedText,
                                int wordCount, String contentHash) {
        this.title = title;
        this.sourceDomain = sourceDomain;
        this.extractedText = extractedText;
        this.wordCount = wordCount;
        this.contentHash = contentHash;
        advanceTo(BookmarkStatus.SUMMARIZING);
    }

    public void applySummary(String summaryText) {
        this.summaryText = summaryText;
        advanceTo(BookmarkStatus.GENERATING_AUDIO);
    }

    public void applyAudio(String audioStorageKey, int audioDurationSec) {
        this.audioStorageKey = audioStorageKey;
        this.audioDurationSec = audioDurationSec;
        this.processedAt = Instant.now();
        advanceTo(BookmarkStatus.READY);
    }

    public void scheduleRetry(String error, Instant nextAttemptAt) {
        this.attempts++;
        this.lastError = error;
        this.nextAttemptAt = nextAttemptAt;
    }

    public void fail(String error) {
        this.attempts++;
        this.lastError = error;
        this.nextAttemptAt = null;
        this.status = BookmarkStatus.FAILED;
    }

    public void softDelete() {
        this.deletedAt = Instant.now();
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    private void advanceTo(BookmarkStatus next) {
        this.status = next;
        this.lastError = null;
        this.nextAttemptAt = null;
    }
}
