package org.timmy.knowcommute.bookmark;

public enum SummaryLength {
    SHORT(300),
    MEDIUM(750),
    LONG(1500);

    private final int targetWords;

    SummaryLength(int targetWords) {
        this.targetWords = targetWords;
    }

    public int targetWords() {
        return targetWords;
    }
}
