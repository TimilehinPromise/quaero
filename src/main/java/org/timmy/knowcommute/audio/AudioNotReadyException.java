package org.timmy.knowcommute.audio;

public class AudioNotReadyException extends RuntimeException {

    public AudioNotReadyException(String status) {
        super("Audio is not ready yet; the bookmark is " + status);
    }
}
