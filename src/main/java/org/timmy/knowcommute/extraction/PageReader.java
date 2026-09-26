package org.timmy.knowcommute.extraction;

import java.net.URI;

/**
 * A hosted service that renders pages a plain fetch cannot read — typically
 * client-rendered app shells — and returns their content. Implementations are selected by
 * {@code readcast.reader.provider}: adding a provider is a new bean declaring its provider
 * name, plus a properties record for its credentials; nothing downstream references any
 * specific service.
 */
public interface PageReader {

    /** The value of {@code readcast.reader.provider} that selects this implementation. */
    String provider();

    /**
     * Render the page at the given URL.
     *
     * @return the rendered content — either {@link RenderedPage#html()} to flow through the
     *         normal classification and extraction pipeline, or {@link RenderedPage#text()}
     *         already reduced to readable text; null when the service rendered nothing
     *         usable, in which case the caller falls back to the metadata brief
     * @throws ExtractionException when the service itself fails; {@link ExtractionException#isRetryable()}
     *         distinguishes transient service errors from account-level ones
     */
    RenderedPage read(URI url);
}
