package org.timmy.knowcommute.figure;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.timmy.knowcommute.bookmark.Bookmark;
import org.timmy.knowcommute.extraction.ExtractedFigure;

@Slf4j
@Component
@RequiredArgsConstructor
public class FigureStore {

    private final BookmarkFigureRepository figures;

    @Transactional
    public void replace(Bookmark bookmark, List<ExtractedFigure> extracted) {
        figures.deleteByBookmarkId(bookmark.getId());
        figures.flush();
        figures.saveAll(extracted.stream()
                .map(figure -> new BookmarkFigure(bookmark.getId(), figure.ordinal(), figure.imageUrl(),
                        figure.altText(), figure.caption(), figure.precedingParagraph(),
                        figure.followingParagraph()))
                .toList());
        log.info("Figure persistence: bookmark={} storedCandidates={}", bookmark.getId(), extracted.size());
    }
}
