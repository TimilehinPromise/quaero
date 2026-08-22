package org.timmy.knowcommute.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.timmy.knowcommute.TestcontainersConfiguration;
import org.timmy.knowcommute.bookmark.Bookmark;
import org.timmy.knowcommute.bookmark.BookmarkRepository;
import org.timmy.knowcommute.bookmark.SummaryLength;
import org.timmy.knowcommute.user.DeviceUser;
import org.timmy.knowcommute.user.DeviceUserRepository;

@SpringBootTest(properties = "readcast.pipeline.enabled=false")
@Import(TestcontainersConfiguration.class)
class ConcurrentClaimTest {

    private static final int BOOKMARKS = 40;
    private static final int WORKERS = 4;

    @Autowired
    private BookmarkClaimer claimer;

    @Autowired
    private BookmarkRepository bookmarks;

    @Autowired
    private DeviceUserRepository users;

    @BeforeEach
    void reset() {
        bookmarks.deleteAllInBatch();
        users.deleteAllInBatch();
        DeviceUser owner = users.saveAndFlush(DeviceUser.withDeviceId(UUID.randomUUID().toString()));
        for (int i = 0; i < BOOKMARKS; i++) {
            bookmarks.save(Bookmark.pending(owner, "https://example.com/" + i, SummaryLength.MEDIUM));
        }
        bookmarks.flush();
    }

    @Test
    void concurrentWorkersNeverClaimTheSameBookmark() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(WORKERS);
        CountDownLatch startTogether = new CountDownLatch(1);
        Instant now = Instant.now();

        try {
            List<Future<List<UUID>>> results = new java.util.ArrayList<>();
            for (int worker = 0; worker < WORKERS; worker++) {
                results.add(pool.submit(() -> {
                    startTogether.await();
                    return claimer.claim(BOOKMARKS / WORKERS, now, now.plus(Duration.ofMinutes(5)));
                }));
            }
            startTogether.countDown();

            List<UUID> allClaims = new java.util.ArrayList<>();
            for (Future<List<UUID>> result : results) {
                allClaims.addAll(result.get(30, TimeUnit.SECONDS));
            }

            assertThat(allClaims).doesNotHaveDuplicates();
            assertThat(allClaims).hasSize(BOOKMARKS);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void everyClaimedBookmarkHasExactlyOneAttemptRecorded() throws Exception {
        concurrentWorkersNeverClaimTheSameBookmark();

        assertThat(bookmarks.findAll())
                .allSatisfy(bookmark -> assertThat(bookmark.getAttempts()).isEqualTo(1));
    }
}
