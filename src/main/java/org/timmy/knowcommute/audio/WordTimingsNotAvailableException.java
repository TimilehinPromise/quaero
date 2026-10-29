package org.timmy.knowcommute.audio;

public class WordTimingsNotAvailableException extends RuntimeException {

    public WordTimingsNotAvailableException() {
        super("This audio was generated before word timestamps were available");
    }
}
