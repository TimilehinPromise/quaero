package org.timmy.knowcommute.extraction;

/**
 * The routing decision for one fetched page.
 *
 * @param kind               the route to take
 * @param bodyWords          prose words in the markup once scripts and chrome are removed —
 *                           the number the routing was based on
 * @param minimumWords       the extraction floor that fits this route: the configured minimum
 *                           for ARTICLE, the lower thin floor for THIN, unused for EMPTY
 * @param javaScriptRendered true when the page carries the markers of an app whose content
 *                           only exists after client-side JavaScript runs
 * @param briefTitle         title for the metadata brief, when a brief is available
 * @param briefText          meta description long enough to narrate as a degraded brief,
 *                           or null when there is nothing worth reading
 */
public record PageClassification(PageKind kind, int bodyWords, int minimumWords,
                                 boolean javaScriptRendered, String briefTitle, String briefText) {
}
