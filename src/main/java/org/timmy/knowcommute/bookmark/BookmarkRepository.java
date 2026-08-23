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
     * Finds an existing summary for identical article text at the same length. Article text is
     * public content, so reusing across users is safe and is the main defence against paying
     * twice for the same article.
     */
    Optional<Bookmark> findFirstByContentHashAndSummaryLengthAndSummaryTextIsNotNull(
            String contentHash, SummaryLength summaryLength);
}
