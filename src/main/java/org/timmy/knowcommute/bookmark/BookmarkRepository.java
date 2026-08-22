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
}
