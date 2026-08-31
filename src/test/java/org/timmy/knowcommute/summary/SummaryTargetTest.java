package org.timmy.knowcommute.summary;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.timmy.knowcommute.bookmark.SummaryLength;

class SummaryTargetTest {

    private static final double THIRD = 0.33;
    private static final int FLOOR = 150;

    private SummaryTarget target(SummaryLength length, int articleWords) {
        return SummaryTarget.forArticle(length, articleWords, THIRD, FLOOR);
    }

    @Test
    void givesALongArticleTheFullRequestedLength() {
        assertThat(target(SummaryLength.LONG, 4896).targetWords())
                .isEqualTo(SummaryLength.LONG.targetWords());
    }

    @Test
    void capsARequestTheArticleCannotSupport() {
        // 2,036 words at LONG is a 74% compression ratio: an abridgement, not a summary.
        assertThat(target(SummaryLength.LONG, 2036).targetWords()).isEqualTo(672);
    }

    @Test
    void neverGoesBelowTheFloorHoweverShortTheArticle() {
        assertThat(target(SummaryLength.LONG, 150).targetWords()).isEqualTo(FLOOR);
        assertThat(target(SummaryLength.SHORT, 10).targetWords()).isEqualTo(FLOOR);
    }

    @Test
    void neverExceedsWhatTheUserAskedFor() {
        assertThat(target(SummaryLength.SHORT, 100_000).targetWords())
                .isEqualTo(SummaryLength.SHORT.targetWords());
    }

    @Test
    void allowsTheSameOvershootToleranceAsBefore() {
        assertThat(target(SummaryLength.LONG, 100_000).allowedWords()).isEqualTo(1950);
    }

    @Test
    void derivesTheParagraphCountFromTheCappedTargetNotTheRequest() {
        // A capped target with an uncapped paragraph count is how the model is talked back
        // over the ceiling: it obeys the paragraph count and ignores the words.
        assertThat(target(SummaryLength.LONG, 2036).paragraphs())
                .isEqualTo(6)
                .isLessThan(target(SummaryLength.LONG, 100_000).paragraphs());
    }

    @Test
    void keepsAtLeastTwoParagraphsForTheShortestNarration() {
        assertThat(target(SummaryLength.SHORT, 10).paragraphs()).isEqualTo(2);
    }
}
