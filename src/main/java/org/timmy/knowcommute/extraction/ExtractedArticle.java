package org.timmy.knowcommute.extraction;

public record ExtractedArticle(
        String title, String author, String sourceDomain, String text, int wordCount, String contentHash,
        java.util.List<ExtractedFigure> figures) {

    public ExtractedArticle {
        figures = figures == null ? java.util.List.of() : java.util.List.copyOf(figures);
    }

    public ExtractedArticle(String title, String author, String sourceDomain, String text,
                            int wordCount, String contentHash) {
        this(title, author, sourceDomain, text, wordCount, contentHash, java.util.List.of());
    }
}
