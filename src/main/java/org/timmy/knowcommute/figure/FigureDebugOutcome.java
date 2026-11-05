package org.timmy.knowcommute.figure;

public record FigureDebugOutcome(
        int ordinal,
        String imageUrl,
        String altText,
        String caption,
        String precedingParagraph,
        String followingParagraph,
        String status,
        String knowledge,
        String mediaType,
        String imageSha256,
        String model,
        Integer promptTokens,
        Integer completionTokens,
        Integer totalTokens,
        String error) {
}
