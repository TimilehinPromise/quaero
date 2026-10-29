package org.timmy.knowcommute.audio;

public class AudioGenerationException extends RuntimeException {

    public AudioGenerationException(String message) {
        super(message);
    }

    public AudioGenerationException(String message, Throwable cause) {
        super(message, cause);
    }
}
