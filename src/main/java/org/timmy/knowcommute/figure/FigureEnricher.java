package org.timmy.knowcommute.figure;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.timmy.knowcommute.bookmark.Bookmark;
import org.timmy.knowcommute.extraction.FetchedPage;

@Slf4j
@Component
@RequiredArgsConstructor
public class FigureEnricher {

    private static final int MAX_ATTEMPTS = 2;

    private final FigureProperties properties;
    private final BookmarkFigureRepository figures;
    private final FigureDescriptionRepository descriptions;
    private final FigureFetcher fetcher;
    private final FigureVisionService vision;

    public EnrichedArticle enrich(Bookmark bookmark) {
        if (!properties.enabled()) {
            return new EnrichedArticle(bookmark.getExtractedText(), bookmark.getContentHash());
        }
        List<BookmarkFigure> candidates = figures.findAllByBookmarkIdOrderByOrdinal(bookmark.getId());
        if (candidates.isEmpty()) {
            log.info("Figure enrichment: bookmark={} candidates=0; using original article", bookmark.getId());
            return new EnrichedArticle(bookmark.getExtractedText(), bookmark.getContentHash());
        }
        log.info("Figure enrichment started: bookmark={} candidates={}", bookmark.getId(), candidates.size());

        String enriched = bookmark.getExtractedText();
        int recovered = 0;
        for (BookmarkFigure figure : candidates) {
            String knowledge = knowledgeFor(bookmark, figure);
            if (knowledge != null) {
                recovered++;
            }
            enriched = enriched.replace("[Figure " + figure.getOrdinal() + "]",
                    knowledge == null ? "" : knowledge);
        }
        enriched = enriched.replaceAll("\\s+", " ").trim();
        log.info("Figure enrichment complete: bookmark={} recovered={} skipped={}",
                bookmark.getId(), recovered, candidates.size() - recovered);
        return new EnrichedArticle(enriched, sha256(enriched));
    }

    private String knowledgeFor(Bookmark bookmark, BookmarkFigure figure) {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                FetchedPage image = fetcher.fetch(URI.create(figure.getImageUrl()));
                String imageHash = sha256(image.body());
                Optional<FigureDescription> cached = descriptions.findById(imageHash);
                if (cached.isPresent()) {
                    log.info("Figure outcome=cached bookmark={} figure={} hasKnowledge={}",
                            bookmark.getId(), figure.getOrdinal(), cached.get().getKnowledge() != null);
                    return cached.get().getKnowledge();
                }

                VisionResult result = vision.extract(bookmark, figure, image);
                log.info("Figure model={} outcome={} bookmark={} figure={} "
                                + "promptTokens={} completionTokens={} totalTokens={}",
                        properties.model(), result.isNone() ? "NONE" : "described",
                        bookmark.getId(), figure.getOrdinal(),
                        result.promptTokens(), result.completionTokens(), result.totalTokens());
                try {
                    descriptions.save(new FigureDescription(imageHash, result.knowledge()));
                } catch (RuntimeException cacheFailure) {
                    log.warn("Could not cache figure extraction for bookmark {} figure {}: {}",
                            bookmark.getId(), figure.getOrdinal(), cacheFailure.getMessage());
                }
                return result.knowledge();
            } catch (RuntimeException failure) {
                // Vision is optional enrichment: no provider, fetch, or cache error may fail the bookmark.
                if (attempt == MAX_ATTEMPTS) {
                    log.warn("Figure model={} outcome=failed bookmark={} figure={}",
                            properties.model(), bookmark.getId(), figure.getOrdinal(), failure);
                }
            }
        }
        return null;
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by the JDK", e);
        }
    }

    private static String sha256(String text) {
        return sha256(text.getBytes(StandardCharsets.UTF_8));
    }
}
