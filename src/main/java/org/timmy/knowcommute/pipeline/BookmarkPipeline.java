package org.timmy.knowcommute.pipeline;

import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.timmy.knowcommute.bookmark.Bookmark;
import org.timmy.knowcommute.bookmark.BookmarkRepository;
import org.timmy.knowcommute.bookmark.BookmarkStatus;

/**
 * Runs one bookmark through one step.
 *
 * <p>The step itself runs outside a transaction: extraction, summarization and speech
 * synthesis are network calls measured in tens of seconds, and holding a pooled connection
 * across one exhausts the pool under trivial load. Only the load and the result write are
 * transactional, and each commits separately so a crash resumes at the next step rather than
 * replaying work already paid for.
 *
 * <p>Boundaries are explicit {@link TransactionTemplate} calls rather than {@code @Transactional}
 * methods: they are invoked from within this class, where Spring's proxy would not apply them.
 */
@Service
public class BookmarkPipeline {

    private final BookmarkRepository bookmarks;
    private final RetryPolicy retryPolicy;
    private final TransactionTemplate transactions;
    private final Map<BookmarkStatus, PipelineStep> steps;

    public BookmarkPipeline(BookmarkRepository bookmarks, RetryPolicy retryPolicy,
                            PlatformTransactionManager transactionManager, List<PipelineStep> steps) {
        this.bookmarks = bookmarks;
        this.retryPolicy = retryPolicy;
        this.transactions = new TransactionTemplate(transactionManager);
        this.steps = new EnumMap<>(BookmarkStatus.class);
        steps.forEach(step -> this.steps.put(step.handles(), step));
    }

    public void process(UUID bookmarkId) {
        Bookmark bookmark = transactions.execute(status -> bookmarks.findById(bookmarkId)
                .filter(candidate -> !candidate.isDeleted())
                .orElse(null));
        if (bookmark == null) {
            return;
        }

        PipelineStep step = steps.get(bookmark.getStatus());
        if (step == null) {
            releaseLease(bookmarkId);
            return;
        }

        try {
            step.execute(bookmark);
            transactions.executeWithoutResult(status -> bookmarks.save(bookmark));
        } catch (Exception e) {
            recordFailure(bookmarkId, e);
        }
    }

    /** No step handles this status, so drop the lease rather than hold the row until it lapses. */
    private void releaseLease(UUID bookmarkId) {
        transactions.executeWithoutResult(status -> bookmarks.findById(bookmarkId)
                .ifPresent(bookmark -> bookmark.scheduleRetry(bookmark.getLastError(), null)));
    }

    private void recordFailure(UUID bookmarkId, Exception cause) {
        transactions.executeWithoutResult(status -> bookmarks.findById(bookmarkId).ifPresent(bookmark -> {
            String message = describe(cause);
            if (retryPolicy.isExhausted(bookmark.getAttempts())) {
                bookmark.fail(message);
            } else {
                bookmark.scheduleRetry(message,
                        retryPolicy.nextAttemptAfter(bookmark.getAttempts(), Instant.now()));
            }
        }));
    }

    private String describe(Exception cause) {
        String message = cause.getMessage();
        return cause.getClass().getSimpleName() + (message == null ? "" : ": " + message);
    }
}
