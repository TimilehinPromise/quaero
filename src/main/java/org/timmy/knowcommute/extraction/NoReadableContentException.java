package org.timmy.knowcommute.extraction;

/**
 * The server answered but the HTML carries no readable article text — typically a
 * client-rendered app shell whose content only exists after JavaScript runs, so a retry of
 * the same fetch returns the same empty bytes. Distinct from {@link ExtractionException}
 * because retrying cannot help; the bookmark goes to BLOCKED and the user is offered the
 * manual-paste path instead.
 */
public class NoReadableContentException extends RuntimeException {

    public NoReadableContentException(String message) {
        super(message);
    }
}
