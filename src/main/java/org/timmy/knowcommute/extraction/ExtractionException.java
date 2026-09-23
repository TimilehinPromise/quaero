package org.timmy.knowcommute.extraction;

public class ExtractionException extends RuntimeException {

    private final boolean retryable;

    public ExtractionException(String message) {
        this(message, true);
    }

    public ExtractionException(String message, Throwable cause) {
        this(message, cause, true);
    }

    public ExtractionException(String message, boolean retryable) {
        super(message);
        this.retryable = retryable;
    }

    public ExtractionException(String message, Throwable cause, boolean retryable) {
        super(message, cause);
        this.retryable = retryable;
    }

    /**
     * False when the bytes already received doom the outcome — too few words, unreadable
     * markup — so a later attempt of the same fetch answers the same way. The pipeline
     * fails such bookmarks immediately instead of burning the whole backoff series.
     */
    public boolean isRetryable() {
        return retryable;
    }
}
