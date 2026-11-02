package org.timmy.knowcommute.figure;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookmarkFigureRepository extends JpaRepository<BookmarkFigure, UUID> {

    List<BookmarkFigure> findAllByBookmarkIdOrderByOrdinal(UUID bookmarkId);

    void deleteByBookmarkId(UUID bookmarkId);
}
