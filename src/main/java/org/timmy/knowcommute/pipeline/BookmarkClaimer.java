package org.timmy.knowcommute.pipeline;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Claims due bookmarks for processing.
 *
 * <p>{@code FOR UPDATE SKIP LOCKED} lets workers take disjoint rows without serialising on a
 * shared lock, and the row locks release with the transaction, so a worker that dies mid-claim
 * strands nothing. The claim also pushes {@code next_attempt_at} a lease into the future,
 * which hides the row from other workers while it is being processed and makes it visible
 * again if this worker never finishes.
 */
@Component
@RequiredArgsConstructor
public class BookmarkClaimer {

    private static final String CLAIM_SQL = """
            UPDATE bookmarks
               SET attempts = attempts + 1,
                   next_attempt_at = ?,
                   updated_at = now()
             WHERE id IN (
                   SELECT id
                     FROM bookmarks
                    WHERE deleted_at IS NULL
                      AND status IN ('PENDING', 'SUMMARIZING', 'GENERATING_AUDIO')
                      AND (next_attempt_at IS NULL OR next_attempt_at <= ?)
                    ORDER BY created_at
                      FOR UPDATE SKIP LOCKED
                    LIMIT ?
                   )
            RETURNING id
            """;

    private final JdbcTemplate jdbcTemplate;

    @Transactional
    public List<UUID> claim(int batchSize, Instant now, Instant leaseUntil) {
        return jdbcTemplate.queryForList(
                CLAIM_SQL, UUID.class, java.sql.Timestamp.from(leaseUntil),
                java.sql.Timestamp.from(now), batchSize);
    }
}
