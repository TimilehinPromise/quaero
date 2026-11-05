package org.timmy.knowcommute.extraction;

public record ExtractedArticle(
        String title, String author, String sourceDomain, String text, int wordCount, String contentHash,
        java.util.List<ExtractedFigure> figures,
        java.util.List<ExtractedFigureDecision> figureDecisions) {

    public ExtractedArticle {
        figures = figures == null ? java.util.List.of() : java.util.List.copyOf(figures);
        figureDecisions = figureDecisions == null ? java.util.List.of() : java.util.List.copyOf(figureDecisions);
    }

    public ExtractedArticle(String title, String author, String sourceDomain, String text, int wordCount,
                            String contentHash, java.util.List<ExtractedFigure> figures) {
        this(title, author, sourceDomain, text, wordCount, contentHash, figures, java.util.List.of());
    }

    public ExtractedArticle(String title, String author, String sourceDomain, String text,
                            int wordCount, String contentHash) {
        this(title, author, sourceDomain, text, wordCount, contentHash, java.util.List.of());
    }
}
