package org.timmy.knowcommute.figure;

import java.util.List;
import org.timmy.knowcommute.extraction.ExtractedFigureDecision;

public record FigureDebugManifest(
        int schemaVersion,
        String bookmarkId,
        String articleUrl,
        String title,
        String startedAt,
        String updatedAt,
        List<ExtractedFigureDecision> decisions,
        List<FigureDebugOutcome> figures,
        List<FigureInsertion> insertions,
        String reusedFromBookmark) {

    public FigureDebugManifest {
        decisions = decisions == null ? List.of() : List.copyOf(decisions);
        figures = figures == null ? List.of() : List.copyOf(figures);
        insertions = insertions == null ? List.of() : List.copyOf(insertions);
    }
}
