package org.timmy.knowcommute.figure;

import java.net.URI;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.timmy.knowcommute.bookmark.UrlValidator;
import org.timmy.knowcommute.extraction.ExtractionProperties;
import org.timmy.knowcommute.extraction.FetchedPage;
import org.timmy.knowcommute.extraction.SecureHttpTransport;
import org.timmy.knowcommute.security.PrivateAddressPolicy;

@Component
public class FigureFetcher {

    private static final MediaType SVG = new MediaType("image", "svg+xml");
    private static final List<MediaType> ACCEPTED_TYPES = List.of(
            MediaType.IMAGE_PNG, MediaType.IMAGE_JPEG, MediaType.IMAGE_GIF,
            new MediaType("image", "webp"), SVG);

    private final SecureHttpTransport transport;
    private final SvgRasterizer rasterizer;

    public FigureFetcher(UrlValidator urlValidator, PrivateAddressPolicy addressPolicy,
                         ExtractionProperties extractionProperties, FigureProperties figureProperties,
                         SvgRasterizer rasterizer) {
        this.transport = new SecureHttpTransport(urlValidator, addressPolicy, extractionProperties,
                figureProperties.maxResponseBytes());
        this.rasterizer = rasterizer;
    }

    public FetchedPage fetch(URI url) {
        FetchedPage image = transport.fetch(url, ACCEPTED_TYPES, null);
        return SVG.includes(image.contentType()) ? rasterizer.rasterize(image) : image;
    }
}
