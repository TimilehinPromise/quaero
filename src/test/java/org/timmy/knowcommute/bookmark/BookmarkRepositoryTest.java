package org.timmy.knowcommute.bookmark;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import jakarta.persistence.EntityManager;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.timmy.knowcommute.RepositoryTest;
import org.timmy.knowcommute.user.DeviceUser;
import org.timmy.knowcommute.user.DeviceUserRepository;

@RepositoryTest
class BookmarkRepositoryTest {

    private static final String ARTICLE_URL = "https://example.com/an-article";

    @Autowired
    private BookmarkRepository bookmarks;

    @Autowired
    private DeviceUserRepository users;

    @Autowired
    private EntityManager entityManager;

    private DeviceUser owner;

    @BeforeEach
    void createOwner() {
        owner = users.saveAndFlush(DeviceUser.withDeviceId("device-owner"));
    }

    @Test
    void newBookmarkStartsPending() {
        Bookmark saved = bookmarks.saveAndFlush(
                Bookmark.pending(owner, ARTICLE_URL, SummaryLength.MEDIUM));

        assertThat(saved.getStatus()).isEqualTo(BookmarkStatus.PENDING);
        assertThat(saved.getAttempts()).isZero();
        assertThat(saved.getUserId()).isNull();

        entityManager.clear();
        assertThat(bookmarks.findById(saved.getId()).orElseThrow().getUserId())
                .isEqualTo(owner.getId());
    }

    @Test
    void rejectsDuplicateUrlForSameUser() {
        bookmarks.saveAndFlush(Bookmark.pending(owner, ARTICLE_URL, SummaryLength.MEDIUM));

        assertThatExceptionOfType(DataIntegrityViolationException.class).isThrownBy(() ->
                bookmarks.saveAndFlush(Bookmark.pending(owner, ARTICLE_URL, SummaryLength.SHORT)));
    }

    @Test
    void allowsSameUrlForDifferentUsers() {
        DeviceUser other = users.saveAndFlush(DeviceUser.withDeviceId("device-other"));

        bookmarks.saveAndFlush(Bookmark.pending(owner, ARTICLE_URL, SummaryLength.MEDIUM));
        bookmarks.saveAndFlush(Bookmark.pending(other, ARTICLE_URL, SummaryLength.MEDIUM));

        assertThat(bookmarks.count()).isEqualTo(2);
    }

    @Test
    void allowsResavingUrlAfterSoftDelete() {
        Bookmark first = bookmarks.saveAndFlush(
                Bookmark.pending(owner, ARTICLE_URL, SummaryLength.MEDIUM));
        first.softDelete();
        bookmarks.saveAndFlush(first);

        Bookmark second = bookmarks.saveAndFlush(
                Bookmark.pending(owner, ARTICLE_URL, SummaryLength.MEDIUM));

        assertThat(second.getId()).isNotEqualTo(first.getId());
    }

    @Test
    void softDeletedBookmarksAreHiddenFromUserQueries() {
        Bookmark bookmark = bookmarks.saveAndFlush(
                Bookmark.pending(owner, ARTICLE_URL, SummaryLength.MEDIUM));
        bookmark.softDelete();
        bookmarks.saveAndFlush(bookmark);

        assertThat(bookmarks.findByIdAndUserIdAndDeletedAtIsNull(bookmark.getId(), owner.getId()))
                .isEmpty();
        assertThat(bookmarks.findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(
                owner.getId(), PageRequest.of(0, 20))).isEmpty();
    }

    @Test
    void pipelineTransitionsWalkThroughToReady() {
        Bookmark bookmark = bookmarks.saveAndFlush(
                Bookmark.pending(owner, ARTICLE_URL, SummaryLength.MEDIUM));

        bookmark.applyExtraction("An Article", "example.com", "body text", 420, "hash");
        assertThat(bookmark.getStatus()).isEqualTo(BookmarkStatus.SUMMARIZING);

        bookmark.applySummary("a summary");
        assertThat(bookmark.getStatus()).isEqualTo(BookmarkStatus.GENERATING_AUDIO);

        bookmark.applyAudio("audio/key.mp3", 180);
        assertThat(bookmark.getStatus()).isEqualTo(BookmarkStatus.READY);
        assertThat(bookmark.getProcessedAt()).isNotNull();

        Bookmark reloaded = bookmarks.saveAndFlush(bookmark);
        assertThat(reloaded.getStatus().isTerminal()).isTrue();
        assertThat(reloaded.getAudioDurationSec()).isEqualTo(180);
    }

    @Test
    void schedulingRetryRecordsErrorWithoutLeavingTerminalState() {
        Bookmark bookmark = bookmarks.saveAndFlush(
                Bookmark.pending(owner, ARTICLE_URL, SummaryLength.MEDIUM));

        bookmark.claim(Instant.now().plusSeconds(300));
        bookmark.scheduleRetry("fetch timed out", Instant.now().plusSeconds(30));
        bookmarks.saveAndFlush(bookmark);

        assertThat(bookmark.getAttempts()).isEqualTo(1);
        assertThat(bookmark.getLastError()).isEqualTo("fetch timed out");
        assertThat(bookmark.getStatus().isTerminal()).isFalse();
    }

    @Test
    void failingClearsRetrySchedule() {
        Bookmark bookmark = bookmarks.saveAndFlush(
                Bookmark.pending(owner, ARTICLE_URL, SummaryLength.MEDIUM));
        bookmark.claim(Instant.now().plusSeconds(300));
        bookmark.scheduleRetry("first failure", Instant.now().plusSeconds(30));

        bookmark.fail("gave up after 3 attempts");
        bookmarks.saveAndFlush(bookmark);

        assertThat(bookmark.getStatus()).isEqualTo(BookmarkStatus.FAILED);
        assertThat(bookmark.getNextAttemptAt()).isNull();
        assertThat(bookmark.getAttempts()).isEqualTo(1);
    }

    @Test
    void advancingClearsPreviousFailure() {
        Bookmark bookmark = bookmarks.saveAndFlush(
                Bookmark.pending(owner, ARTICLE_URL, SummaryLength.MEDIUM));
        bookmark.claim(Instant.now().plusSeconds(300));
        bookmark.scheduleRetry("transient failure", Instant.now().plusSeconds(30));

        bookmark.applyExtraction("An Article", "example.com", "body text", 420, "hash");

        assertThat(bookmark.getLastError()).isNull();
        assertThat(bookmark.getNextAttemptAt()).isNull();
        assertThat(bookmark.getAttempts()).isZero();
    }
}
