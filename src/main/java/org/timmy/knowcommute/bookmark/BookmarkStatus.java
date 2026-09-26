package org.timmy.knowcommute.bookmark;

public enum BookmarkStatus {
    PENDING,
    SUMMARIZING,
    GENERATING_AUDIO,
    READY,
    FAILED,
    /** A server-side fetch cannot reach the content — the site refused, or the page renders it
     * with JavaScript. Retrying the same fetch will not help; the user can paste the text. */
    BLOCKED;

    public boolean isTerminal() {
        return this == READY || this == FAILED || this == BLOCKED;
    }
}
