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

    @Column(length = 255)
    private String author;

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

    public void applyExtraction(String title, String author, String sourceDomain, String extractedText,
                                int wordCount, String contentHash) {
        this.title = title;
        this.author = author;
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

    /**
     * Counts the attempt before the work runs, so a step that kills the worker still burns an
     * attempt and cannot loop forever. {@code leaseUntil} hides the row from other workers;
     * if this one dies, the lease lapses and the row is picked up again.
     */
    public void claim(Instant leaseUntil) {
        this.attempts++;
        this.nextAttemptAt = leaseUntil;
    }

    public void scheduleRetry(String error, Instant nextAttemptAt) {
        this.lastError = error;
        this.nextAttemptAt = nextAttemptAt;
    }

    /** Terminal without burning retries: a refused fetch will be refused again. */
    public void block(String reason) {
        this.lastError = reason;
        this.nextAttemptAt = null;
        this.status = BookmarkStatus.BLOCKED;
    }

    /** Recovery from BLOCKED: the user supplied the text a fetch could not reach. */
    public void applyPastedContent(String title, String text, int wordCount, String contentHash) {
        this.title = title;
        this.extractedText = text;
        this.wordCount = wordCount;
        this.contentHash = contentHash;
        this.attempts = 0;
        advanceTo(BookmarkStatus.SUMMARIZING);
    }

    public void fail(String error) {
        this.lastError = error;
        this.nextAttemptAt = null;
        this.status = BookmarkStatus.FAILED;
    }

    /** Recovery from FAILED or BLOCKED: start the pipeline over from extraction. */
    public void requeue() {
        advanceTo(BookmarkStatus.PENDING);
    }

    public void softDelete() {
        this.deletedAt = Instant.now();
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    /** Attempts are per-step, so clearing them here keeps a long pipeline from exhausting them. */
    private void advanceTo(BookmarkStatus next) {
        this.status = next;
        this.attempts = 0;
        this.lastError = null;
        this.nextAttemptAt = null;
    }
}
