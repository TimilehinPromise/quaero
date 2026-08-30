package org.timmy.knowcommute.summary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.timmy.knowcommute.TestcontainersConfiguration;
import org.timmy.knowcommute.bookmark.Bookmark;
import org.timmy.knowcommute.bookmark.BookmarkRepository;
import org.timmy.knowcommute.bookmark.BookmarkStatus;
import org.timmy.knowcommute.bookmark.SummaryLength;
import org.timmy.knowcommute.user.DeviceUser;
import org.timmy.knowcommute.user.DeviceUserRepository;

@SpringBootTest(properties = "readcast.pipeline.enabled=false")
@Import(TestcontainersConfiguration.class)
class SummarizationStepTest {

    private static final String HASH = "a".repeat(64);

    @Autowired
    private BookmarkRepository bookmarks;

    @Autowired
    private DeviceUserRepository users;

    private final AtomicInteger modelCalls = new AtomicInteger();
    /** Replies are consumed in order; once drained the stub keeps answering with the default. */
    private final Queue<String> serviceReplies = new ConcurrentLinkedQueue<>();
    private DeviceUser owner;
    private SummarizationStep step;

    @BeforeEach
    void setUp() {
        bookmarks.deleteAllInBatch();
        users.deleteAllInBatch();
        owner = users.saveAndFlush(DeviceUser.withDeviceId(UUID.randomUUID().toString()));
        modelCalls.set(0);
        serviceReplies.clear();

        SummarizationService service = new SummarizationService(null, null, null) {
            @Override
            public String summarise(String title, String domain, String article, SummaryLength length) {
                modelCalls.incrementAndGet();
                String next = serviceReplies.poll();
                return next == null ? "freshly generated summary" : next;
            }
        };
        step = new SummarizationStep(service, bookmarks);
    }

    private Bookmark givenExtracted(String url, String hash) {
        Bookmark bookmark = Bookmark.pending(owner, url, SummaryLength.MEDIUM);
        bookmark.applyExtraction("A Title", null, "example.com", "the article body", 400, hash);
        return bookmarks.saveAndFlush(bookmark);
    }

    @Test
    void summarisesAndAdvancesToAudioGeneration() {
        Bookmark bookmark = givenExtracted("https://example.com/one", HASH);

        step.execute(bookmark);

        assertThat(bookmark.getSummaryText()).isEqualTo("freshly generated summary");
        assertThat(bookmark.getStatus()).isEqualTo(BookmarkStatus.GENERATING_AUDIO);
        assertThat(modelCalls).hasValue(1);
    }

    @Test
    void reusesAnExistingSummaryForIdenticalContentInsteadOfPayingAgain() {
        Bookmark first = givenExtracted("https://example.com/one", HASH);
        step.execute(first);
        bookmarks.saveAndFlush(first);

        Bookmark duplicate = givenExtracted("https://other.example.com/same-article", HASH);
        step.execute(duplicate);

        assertThat(duplicate.getSummaryText()).isEqualTo(first.getSummaryText());
        assertThat(duplicate.getStatus()).isEqualTo(BookmarkStatus.GENERATING_AUDIO);
        assertThat(modelCalls).hasValue(1);
    }

    @Test
    void doesNotReuseAcrossDifferentSummaryLengths() {
        Bookmark medium = givenExtracted("https://example.com/one", HASH);
        step.execute(medium);
        bookmarks.saveAndFlush(medium);

        Bookmark shortVersion = Bookmark.pending(owner, "https://example.com/two", SummaryLength.SHORT);
        shortVersion.applyExtraction("A Title", null, "example.com", "the article body", 400, HASH);
        step.execute(bookmarks.saveAndFlush(shortVersion));

        assertThat(modelCalls).hasValue(2);
    }

    @Test
    void doesNotReuseAcrossDifferentContent() {
        step.execute(givenExtracted("https://example.com/one", HASH));
        step.execute(givenExtracted("https://example.com/two", "b".repeat(64)));

        assertThat(modelCalls).hasValue(2);
    }

    @Test
    void regeneratesInsteadOfReusingACachedSummaryThatBreachesTheCeiling() {
        // A summary cached before the ceiling gate existed: 1,000 words against MEDIUM's 975 allowance.
        serviceReplies.add("word ".repeat(1_000));
        Bookmark first = givenExtracted("https://example.com/one", HASH);
        step.execute(first);
        bookmarks.saveAndFlush(first);

        Bookmark duplicate = givenExtracted("https://other.example.com/same-article", HASH);
        step.execute(duplicate);

        assertThat(duplicate.getSummaryText()).isEqualTo("freshly generated summary");
        assertThat(duplicate.getStatus()).isEqualTo(BookmarkStatus.GENERATING_AUDIO);
        assertThat(modelCalls).hasValue(2);
    }

    @Test
    void refusesToSummariseABookmarkWithNoExtractedText() {
        Bookmark bookmark = Bookmark.pending(owner, "https://example.com/empty", SummaryLength.MEDIUM);

        assertThatExceptionOfType(SummarizationException.class)
                .isThrownBy(() -> step.execute(bookmark));
        assertThat(modelCalls).hasValue(0);
    }

    @Test
    void handlesTheSummarizingStatus() {
        assertThat(step.handles()).isEqualTo(BookmarkStatus.SUMMARIZING);
    }
}
