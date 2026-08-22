package org.timmy.knowcommute.extraction;

/**
 * The site refused a server-side fetch — a bot challenge, an auth wall, or a hard rate limit.
 * Distinct from {@link ExtractionException} because retrying cannot fix it; the bookmark goes
 * to BLOCKED and the user is offered the manual-paste path instead.
 */
public class SiteBlockedException extends RuntimeException {

    public SiteBlockedException(String message) {
        super(message);
    }
}
