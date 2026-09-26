package org.timmy.knowcommute.extraction;

/**
 * The normalized result of one hosted-renderer call. Exactly one of {@link #html()} or
 * {@link #text()} is non-null: HTML flows back through the classifier and extractor, text is
 * already readable and is used as-is.
 *
 * @param title the page title if the service reported one, null otherwise — the caller falls
 *              back to the metadata title
 * @param html  the fully rendered document markup
 * @param text  readable text (markdown) extracted from the rendered page
 */
public record RenderedPage(String title, String html, String text) {
}
