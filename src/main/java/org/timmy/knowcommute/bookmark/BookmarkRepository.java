package org.timmy.knowcommute.bookmark;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookmarkRepository extends JpaRepository<Bookmark, UUID> {

    Optional<Bookmark> findByIdAndUserIdAndDeletedAtIsNull(UUID id, UUID userId);

    Page<Bookmark> findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    Page<Bookmark> findByUserIdAndStatusAndDeletedAtIsNullOrderByCreatedAtDesc(
            UUID userId, BookmarkStatus status, Pageable pageable);

    boolean existsByUserIdAndUrlAndDeletedAtIsNull(UUID userId, String url);

    /**
     * Finds an existing plain summary for identical summariser input at the same length. The
     * input includes any figure knowledge recovered before this lookup. Article text is public
     * content, so reusing across users is safe and is the main defence against paying twice for
     * the same article. A bookmark that asked for a lens is never a candidate, even one whose
     * brief failed and fell back to plain.
     */
    Optional<Bookmark> findFirstByContentHashAndSummaryLengthAndLensProfileHashIsNullAndSummaryTextIsNotNull(
            String contentHash, SummaryLength summaryLength);

    /**
     * The lensed counterpart: same article, length and profile text. Requires a stored brief, so
     * a summary that fell back to plain is not handed out as if the lens had worked.
     */
    Optional<Bookmark>
            findFirstByContentHashAndSummaryLengthAndLensProfileHashAndLensBriefIsNotNullAndSummaryTextIsNotNull(
            String contentHash, SummaryLength summaryLength, String lensProfileHash);
}
