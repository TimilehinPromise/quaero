package org.timmy.knowcommute.figure;

import java.util.List;
import org.timmy.knowcommute.extraction.ExtractedFigureDecision;

public record FigureDebugRunDetail(
        String id,
        String title,
        String articleUrl,
        String updatedAt,
        String originalArticle,
        String enrichedArticle,
        String summary,
        int originalWordCount,
        int enrichedWordCount,
        int summaryWordCount,
        String reusedFromBookmark,
        List<ExtractedFigureDecision> decisions,
        List<Figure> figures,
        List<FigureInsertion> insertions) {

    public record Figure(
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
            String error,
            String prompt,
            String response,
            boolean hasImage) {
    }
}
