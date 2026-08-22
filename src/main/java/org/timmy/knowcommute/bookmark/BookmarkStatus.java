package org.timmy.knowcommute.bookmark;

public enum BookmarkStatus {
    PENDING,
    SUMMARIZING,
    GENERATING_AUDIO,
    READY,
    FAILED;

    public boolean isTerminal() {
        return this == READY || this == FAILED;
    }
}
