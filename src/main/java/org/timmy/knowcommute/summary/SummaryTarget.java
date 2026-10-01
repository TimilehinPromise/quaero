package org.timmy.knowcommute.summary;

import org.timmy.knowcommute.bookmark.SummaryLength;

/**
 * How many words of narration to aim for: the length the user asked for, or as much as the
 * article can honestly support, whichever is smaller.
 *
 * <p>Applying the requested length blind produces nonsense at the short end. LONG asks for
 * 1,500 words, and against a 2,000-word article that is an abridgement rather than a summary —
 * the model can only reach the count by walking through the article section by section, which
 * is both what the prompt forbids and the overshoot mechanism recorded in devlog 0012.
 */
record SummaryTarget(int targetWords) {

    /**
     * A model cannot count its own words, so the prompt's ceiling is only advisory. Overshoot
     * within this factor is accepted — tighter and normal variation trips the retry constantly,
     * looser and a MEDIUM episode silently runs long.
     */
    private static final double CEILING_TOLERANCE = 1.30;

    /** The prompt asks for a paragraph count because a model can obey one; it cannot count words. */
    private static final int WORDS_PER_PARAGRAPH = 110;

    private static final int MINIMUM_PARAGRAPHS = 2;

    static SummaryTarget forArticle(SummaryLength length, int articleWords,
                                    double maxSourceFraction, int minWords) {
        int supported = Math.max((int) Math.round(articleWords * maxSourceFraction), minWords);
        return new SummaryTarget(Math.min(length.targetWords(), supported));
    }

    /**
     * The narration's share once a takeaway is set aside for it. Floored at half, so a long
     * takeaway on a short article cannot starve the summary it is meant to close.
     */
    SummaryTarget reserving(int words) {
        return new SummaryTarget(Math.max(targetWords - words, targetWords / 2));
    }

    int allowedWords() {
        return (int) (targetWords * CEILING_TOLERANCE);
    }

    int paragraphs() {
        return Math.max(targetWords / WORDS_PER_PARAGRAPH, MINIMUM_PARAGRAPHS);
    }
}
