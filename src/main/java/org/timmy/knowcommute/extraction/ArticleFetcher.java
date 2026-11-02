package org.timmy.knowcommute.extraction;

import java.net.URI;
import java.util.Comparator;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.timmy.knowcommute.bookmark.UrlValidator;
import org.timmy.knowcommute.security.PrivateAddressPolicy;

@Component
public class ArticleFetcher {

    /**
     * Absent Content-Type is read as HTML rather than refused. Servers that omit it are
     * overwhelmingly serving pages, and refusing them would reject articles that work today.
     */
    private static final MediaType ASSUMED_TYPE = MediaType.TEXT_HTML;

    private final SecureHttpTransport transport;
    private final List<MediaType> acceptedTypes;

    public ArticleFetcher(UrlValidator urlValidator, PrivateAddressPolicy addressPolicy,
                          ExtractionProperties properties, List<DocumentExtractor> extractors) {
        this.transport = new SecureHttpTransport(
                urlValidator, addressPolicy, properties, properties.maxResponseBytes());
        this.acceptedTypes = extractors.stream()
                .flatMap(extractor -> extractor.supportedTypes().stream())
                .distinct()
                .sorted(Comparator.comparing(MediaType::toString))
                .toList();
    }

    public FetchedPage fetch(URI url) {
        return transport.fetch(url, acceptedTypes, ASSUMED_TYPE);
    }
}
