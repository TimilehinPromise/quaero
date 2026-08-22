package org.timmy.knowcommute.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.timmy.knowcommute.TestcontainersConfiguration;
import org.timmy.knowcommute.bookmark.Bookmark;
import org.timmy.knowcommute.bookmark.BookmarkRepository;
import org.timmy.knowcommute.bookmark.BookmarkStatus;
import org.timmy.knowcommute.bookmark.SummaryLength;
import org.timmy.knowcommute.extraction.SiteBlockedException;
import org.timmy.knowcommute.user.DeviceUser;
import org.timmy.knowcommute.user.DeviceUserRepository;

@SpringBootTest(properties = "readcast.pipeline.enabled=false")
@Import(TestcontainersConfiguration.class)
class BookmarkPipelineTest {

    @Autowired
    private BookmarkRepository bookmarks;

    @Autowired
    private DeviceUserRepository users;

    @Autowired
    private BookmarkClaimer claimer;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private PipelineProperties properties;

    private DeviceUser owner;

    @BeforeEach
    void reset() {
        bookmarks.deleteAllInBatch();
        users.deleteAllInBatch();
        owner = users.saveAndFlush(DeviceUser.withDeviceId(UUID.randomUUID().toString()));
    }

    private UUID givenPendingBookmark() {
        return bookmarks.saveAndFlush(
                Bookmark.pending(owner, "https://example.com/" + UUID.randomUUID(),
                        SummaryLength.MEDIUM)).getId();
    }

    private BookmarkPipeline pipelineWith(PipelineStep... steps) {
        return new BookmarkPipeline(bookmarks, new RetryPolicy(properties),
                transactionManager, List.of(steps));
    }

    private Bookmark reload(UUID id) {
        return bookmarks.findById(id).orElseThrow();
    }

    private UUID claimOne() {
        Instant now = Instant.now();
        List<UUID> claimed = claimer.claim(10, now, now.plus(Duration.ofMinutes(5)));
        assertThat(claimed).hasSize(1);
        return claimed.getFirst();
    }

    @Test
    void walksAPendingBookmarkThroughToReady() {
        UUID id = givenPendingBookmark();
        BookmarkPipeline pipeline = pipelineWith(
                new RecordingStep(BookmarkStatus.PENDING,
                        b -> b.applyExtraction("Title", "example.com", "text", 400, "hash")),
                new RecordingStep(BookmarkStatus.SUMMARIZING, b -> b.applySummary("summary")),
                new RecordingStep(BookmarkStatus.GENERATING_AUDIO, b -> b.applyAudio("key.mp3", 120)));

        pipeline.process(claimOne());
        assertThat(reload(id).getStatus()).isEqualTo(BookmarkStatus.SUMMARIZING);

        pipeline.process(claimOne());
        assertThat(reload(id).getStatus()).isEqualTo(BookmarkStatus.GENERATING_AUDIO);

        pipeline.process(claimOne());
        Bookmark done = reload(id);
        assertThat(done.getStatus()).isEqualTo(BookmarkStatus.READY);
        assertThat(done.getAudioStorageKey()).isEqualTo("key.mp3");
        assertThat(done.getProcessedAt()).isNotNull();
    }

    @Test
    void advancingResetsAttemptsSoALongPipelineDoesNotExhaustThem() {
        UUID id = givenPendingBookmark();
        BookmarkPipeline pipeline = pipelineWith(new RecordingStep(BookmarkStatus.PENDING,
                b -> b.applyExtraction("Title", "example.com", "text", 400, "hash")));

        pipeline.process(claimOne());

        assertThat(reload(id).getAttempts()).isZero();
    }

    @Test
    void failsTerminallyAfterMaxAttempts() {
        UUID id = givenPendingBookmark();
        BookmarkPipeline pipeline = pipelineWith(
                RecordingStep.alwaysFailing(BookmarkStatus.PENDING, "extraction exploded"));

        for (int attempt = 1; attempt <= properties.maxAttempts(); attempt++) {
            Instant now = Instant.now().plus(Duration.ofHours(attempt));
            List<UUID> claimed = claimer.claim(10, now, now.plus(Duration.ofMinutes(5)));
            assertThat(claimed).containsExactly(id);
            pipeline.process(id);
        }

        Bookmark failed = reload(id);
        assertThat(failed.getStatus()).isEqualTo(BookmarkStatus.FAILED);
        assertThat(failed.getLastError()).contains("extraction exploded");
        assertThat(failed.getNextAttemptAt()).isNull();
        assertThat(failed.getAttempts()).isEqualTo(properties.maxAttempts());
    }

    @Test
    void backsOffBeforeRetryingRatherThanSpinning() {
        UUID id = givenPendingBookmark();
        BookmarkPipeline pipeline = pipelineWith(
                RecordingStep.alwaysFailing(BookmarkStatus.PENDING, "transient"));

        pipeline.process(claimOne());

        Bookmark retrying = reload(id);
        assertThat(retrying.getStatus()).isEqualTo(BookmarkStatus.PENDING);
        assertThat(retrying.getNextAttemptAt()).isAfter(Instant.now());
        assertThat(claimer.claim(10, Instant.now(), Instant.now().plus(Duration.ofMinutes(5))))
                .isEmpty();
    }

    @Test
    void leavesSoftDeletedBookmarksAlone() {
        UUID id = givenPendingBookmark();
        Bookmark bookmark = reload(id);
        bookmark.softDelete();
        bookmarks.saveAndFlush(bookmark);

        assertThat(claimer.claim(10, Instant.now(), Instant.now().plus(Duration.ofMinutes(5))))
                .isEmpty();
    }

    @Test
    void ignoresTerminalBookmarks() {
        UUID id = givenPendingBookmark();
        Bookmark bookmark = reload(id);
        bookmark.applyExtraction("t", "example.com", "x", 1, "h");
        bookmark.applySummary("s");
        bookmark.applyAudio("k", 1);
        bookmarks.saveAndFlush(bookmark);

        assertThat(claimer.claim(10, Instant.now(), Instant.now().plus(Duration.ofMinutes(5))))
                .isEmpty();
    }

    @Test
    void aClaimHidesTheRowFromOtherWorkersUntilTheLeaseLapses() {
        givenPendingBookmark();
        Instant now = Instant.now();

        assertThat(claimer.claim(10, now, now.plus(Duration.ofMinutes(5)))).hasSize(1);
        assertThat(claimer.claim(10, now, now.plus(Duration.ofMinutes(5)))).isEmpty();

        Instant afterLease = now.plus(Duration.ofMinutes(6));
        assertThat(claimer.claim(10, afterLease, afterLease.plus(Duration.ofMinutes(5))))
                .hasSize(1);
    }

    @Test
    void claimBurnsAnAttemptSoAWorkerThatDiesCannotLoopForever() {
        UUID id = givenPendingBookmark();
        Instant now = Instant.now();

        claimer.claim(10, now, now.plus(Duration.ofSeconds(1)));
        assertThat(reload(id).getAttempts()).isEqualTo(1);

        Instant later = now.plus(Duration.ofSeconds(2));
        claimer.claim(10, later, later.plus(Duration.ofSeconds(1)));
        assertThat(reload(id).getAttempts()).isEqualTo(2);
    }

    @Test
    void aBlockedSiteGoesStraightToBlockedWithoutBurningRetries() {
        UUID id = givenPendingBookmark();
        BookmarkPipeline pipeline = pipelineWith(new RecordingStep(BookmarkStatus.PENDING, b -> {
            throw new SiteBlockedException("Site refused the request (HTTP 403)");
        }));

        pipeline.process(claimOne());

        Bookmark blocked = reload(id);
        assertThat(blocked.getStatus()).isEqualTo(BookmarkStatus.BLOCKED);
        assertThat(blocked.getStatus().isTerminal()).isTrue();
        assertThat(blocked.getAttempts()).isEqualTo(1);
        assertThat(blocked.getNextAttemptAt()).isNull();
        assertThat(claimer.claim(10, Instant.now(), Instant.now().plus(Duration.ofMinutes(5))))
                .isEmpty();
    }

    @Test
    void pastedContentPutsABlockedBookmarkBackIntoThePipeline() {
        UUID id = givenPendingBookmark();
        Bookmark bookmark = reload(id);
        bookmark.block("Site refused the request (HTTP 403)");
        bookmarks.saveAndFlush(bookmark);

        bookmark.applyPastedContent("Pasted Title", "some pasted body text", 4, "hash");
        bookmarks.saveAndFlush(bookmark);

        Bookmark recovered = reload(id);
        assertThat(recovered.getStatus()).isEqualTo(BookmarkStatus.SUMMARIZING);
        assertThat(recovered.getLastError()).isNull();
        assertThat(claimer.claim(10, Instant.now(), Instant.now().plus(Duration.ofMinutes(5))))
                .containsExactly(id);
    }

    @Test
    void doesNothingWhenNoStepHandlesTheStatus() {
        UUID id = givenPendingBookmark();
        BookmarkPipeline pipeline = pipelineWith(
                new RecordingStep(BookmarkStatus.GENERATING_AUDIO, b -> b.applyAudio("k", 1)));

        pipeline.process(claimOne());

        Bookmark untouched = reload(id);
        assertThat(untouched.getStatus()).isEqualTo(BookmarkStatus.PENDING);
        assertThat(untouched.getNextAttemptAt()).isNull();
    }
}
