package org.timmy.knowcommute.extraction;

public record ExtractedFigureDecision(
        int position,
        Integer ordinal,
        String imageUrl,
        String altText,
        String caption,
        String precedingParagraph,
        String followingParagraph,
        String decision,
        String reason,
        Score score) {

    public record Score(
            double total,
            double caption,
            double adjacentCaption,
            double figureWrapper,
            double junkUrl,
            double altText,
            double documentPosition) {

        public static Score zero() {
            return new Score(0, 0, 0, 0, 0, 0, 0);
        }
    }
}
