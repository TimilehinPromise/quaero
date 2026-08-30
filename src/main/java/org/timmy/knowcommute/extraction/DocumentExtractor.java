package org.timmy.knowcommute.extraction;

import java.util.Set;
import org.springframework.http.MediaType;

/**
 * Turns one document format into an article. The response content type picks the
 * implementation, so supporting a new format is a new bean rather than a branch in the
 * pipeline, and the fetcher derives both its {@code Accept} header and its rejection rule
 * from the same declarations.
 */
public interface DocumentExtractor {

    Set<MediaType> supportedTypes();

    ExtractedArticle extract(FetchedPage page);

    default boolean supports(MediaType contentType) {
        return contentType != null
                && supportedTypes().stream().anyMatch(supported -> supported.includes(contentType));
    }
}
