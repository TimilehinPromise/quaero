package org.timmy.knowcommute.extraction;

public record ExtractedFigure(
        int ordinal,
        String imageUrl,
        String altText,
        String caption,
        String precedingParagraph,
        String followingParagraph) {
}
