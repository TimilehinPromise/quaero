package org.timmy.knowcommute.figure;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.timmy.knowcommute.bookmark.Bookmark;
import org.timmy.knowcommute.extraction.FetchedPage;

@Slf4j
@Component
public class FigureEnricher {

    private static final int MAX_ATTEMPTS = 2;
    private static final int CONTEXT_CHARACTERS = 180;

    private final FigureProperties properties;
    private final BookmarkFigureRepository figures;
    private final FigureDescriptionRepository descriptions;
    private final FigureFetcher fetcher;
    private final FigureVisionService vision;
    private final FigureDebugService debug;

    @Autowired
    public FigureEnricher(FigureProperties properties, BookmarkFigureRepository figures,
                          FigureDescriptionRepository descriptions, FigureFetcher fetcher,
                          FigureVisionService vision, FigureDebugService debug) {
        this.properties = properties;
        this.figures = figures;
        this.descriptions = descriptions;
        this.fetcher = fetcher;
        this.vision = vision;
        this.debug = debug;
    }

    public FigureEnricher(FigureProperties properties, BookmarkFigureRepository figures,
                          FigureDescriptionRepository descriptions, FigureFetcher fetcher, FigureVisionService vision) {
        this(properties, figures, descriptions, fetcher, vision,
                new FigureDebugService(new FigureDebugProperties("")));
    }

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
        String marker = "\uE000" + java.util.UUID.randomUUID() + "\uE001";
        int recovered = 0;
        for (BookmarkFigure figure : candidates) {
            String knowledge = knowledgeFor(bookmark, figure);
            if (knowledge != null) {
                recovered++;
            }
            String startMarker = marker + "S" + figure.getOrdinal() + marker;
            String endMarker = marker + "E" + figure.getOrdinal() + marker;
            enriched = enriched.replace("[Figure " + figure.getOrdinal() + "]",
                    knowledge == null ? "" : startMarker + knowledge + endMarker);
        }
        enriched = enriched.replaceAll("\\s+", " ").trim();
        InsertionResult result = insertions(enriched, candidates, marker);
        log.info("Figure enrichment complete: bookmark={} recovered={} skipped={}",
                bookmark.getId(), recovered, candidates.size() - recovered);
        return new EnrichedArticle(result.text(), sha256(result.text()), result.insertions());
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
                    debug.cached(bookmark, figure, image, cached.get().getKnowledge());
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
                debug.vision(bookmark, figure, image, result, properties.model());
                return result.knowledge();
            } catch (RuntimeException failure) {
                // Vision is optional enrichment: no provider, fetch, or cache error may fail the bookmark.
                if (attempt == MAX_ATTEMPTS) {
                    log.warn("Figure model={} outcome=failed bookmark={} figure={}",
                            properties.model(), bookmark.getId(), figure.getOrdinal(), failure);
                    debug.failure(bookmark, figure, failure);
                }
            }
        }
        return null;
    }

    private static InsertionResult insertions(String marked, List<BookmarkFigure> candidates, String marker) {
        StringBuilder text = new StringBuilder(marked.length());
        List<FigureInsertion> insertions = new ArrayList<>();
        int cursor = 0;
        for (BookmarkFigure figure : candidates) {
            String startMarker = marker + "S" + figure.getOrdinal() + marker;
            String endMarker = marker + "E" + figure.getOrdinal() + marker;
            int markedStart = marked.indexOf(startMarker, cursor);
            if (markedStart < 0) {
                insertions.add(FigureInsertion.notInserted(figure.getOrdinal()));
                continue;
            }
            int contentStart = markedStart + startMarker.length();
            int markedEnd = marked.indexOf(endMarker, contentStart);
            text.append(marked, cursor, markedStart);
            int start = text.length();
            String inserted = marked.substring(contentStart, markedEnd);
            text.append(inserted);
            int end = text.length();
            insertions.add(new FigureInsertion(figure.getOrdinal(), inserted, start, end, null, null));
            cursor = markedEnd + endMarker.length();
        }
        text.append(marked, cursor, marked.length());
        String enriched = text.toString();
        List<FigureInsertion> withContext = insertions.stream()
                .map(insertion -> insertion.startOffset() < 0 ? insertion
                        : FigureInsertion.inserted(insertion.figureOrdinal(), insertion.insertedText(),
                                insertion.startOffset(), insertion.endOffset(),
                                enriched.substring(Math.max(0, insertion.startOffset() - CONTEXT_CHARACTERS),
                                        insertion.startOffset()),
                                enriched.substring(insertion.endOffset(),
                                        Math.min(enriched.length(), insertion.endOffset() + CONTEXT_CHARACTERS))))
                .toList();
        return new InsertionResult(enriched, withContext);
    }

    private record InsertionResult(String text, List<FigureInsertion> insertions) {
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
