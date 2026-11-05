package org.timmy.knowcommute.figure;

import java.util.List;

public record EnrichedArticle(String text, String contentHash, List<FigureInsertion> insertions) {

    public EnrichedArticle {
        insertions = insertions == null ? List.of() : List.copyOf(insertions);
    }

    public EnrichedArticle(String text, String contentHash) {
        this(text, contentHash, List.of());
    }
}
