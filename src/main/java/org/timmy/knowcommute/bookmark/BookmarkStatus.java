package org.timmy.knowcommute.bookmark;

public enum BookmarkStatus {
    PENDING,
    SUMMARIZING,
    GENERATING_AUDIO,
    READY,
    FAILED,
    /** The site refused a server-side fetch. Retrying will not help; the user can paste the text. */
    BLOCKED;

    public boolean isTerminal() {
        return this == READY || this == FAILED || this == BLOCKED;
    }
}
