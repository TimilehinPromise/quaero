package org.timmy.knowcommute.extraction;

/**
 * The URL resolved to something that cannot become narration — a video, an image, a PDF that
 * forbids text extraction. Terminal on the first attempt rather than retried: the same URL
 * returns the same bytes, so a retry only delays the same answer.
 */
public class UnsupportedContentException extends RuntimeException {

    public UnsupportedContentException(String message) {
        super(message);
    }
}
