package org.timmy.knowcommute.extraction;

import java.net.URI;
import org.springframework.http.MediaType;

/**
 * Raw bytes rather than a decoded string: the body may be a PDF, and the charset of an HTML
 * body is often declared inside the document rather than in the header.
 */
public record FetchedPage(URI finalUrl, MediaType contentType, byte[] body) {
}
