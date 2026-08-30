package org.timmy.knowcommute.extraction;

public record ExtractedArticle(
        String title, String author, String sourceDomain, String text, int wordCount, String contentHash) {
}
