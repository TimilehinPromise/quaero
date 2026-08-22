package org.timmy.knowcommute.extraction;

public record ExtractedArticle(
        String title, String sourceDomain, String text, int wordCount, String contentHash) {
}
