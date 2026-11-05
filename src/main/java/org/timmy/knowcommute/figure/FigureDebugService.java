package org.timmy.knowcommute.figure;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.function.UnaryOperator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.timmy.knowcommute.bookmark.Bookmark;
import org.timmy.knowcommute.extraction.ExtractedArticle;
import org.timmy.knowcommute.extraction.ExtractedFigureDecision;
import org.timmy.knowcommute.extraction.FetchedPage;

@Slf4j
@Service
public class FigureDebugService {

    private static final int MANIFEST_VERSION = 1;

    private final FigureDebugProperties properties;
    private final Path root;
    private final ObjectMapper json = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public FigureDebugService(FigureDebugProperties properties) {
        this.properties = properties;
        this.root = properties.enabled() ? Path.of(properties.directory()) : null;
    }

    public void extraction(Bookmark bookmark, ExtractedArticle article) {
        updateManifest(bookmark, previous -> new FigureDebugManifest(MANIFEST_VERSION, id(bookmark),
                bookmark.getUrl(), article.title(), Instant.now().toString(), Instant.now().toString(),
                article.figureDecisions(), List.of(), List.of(), null));
    }

    public void article(Bookmark bookmark, String article) {
        write(bookmark.getId(), "article.txt", article);
        updateManifest(bookmark, UnaryOperator.identity());
    }

    public void enriched(Bookmark bookmark, String article) {
        write(bookmark.getId(), "enriched-article.txt", article);
    }

    public void enriched(Bookmark bookmark, EnrichedArticle article) {
        enriched(bookmark, article.text());
        updateManifest(bookmark, previous -> copy(previous, previous.decisions(), previous.figures(),
                article.insertions(), previous.reusedFromBookmark()));
    }

    public void summary(Bookmark bookmark, String summary, UUID reusedFrom) {
        String provenance = reusedFrom == null ? "" : "reusedFromBookmark=" + reusedFrom + "\n\n";
        write(bookmark.getId(), "summary.txt", provenance + summary);
        updateManifest(bookmark, previous -> copy(previous, previous.decisions(), previous.figures(),
                previous.insertions(), reusedFrom == null ? null : reusedFrom.toString()));
    }

    public void request(Bookmark bookmark, BookmarkFigure figure, FetchedPage image, String prompt) {
        image(bookmark, figure, image);
        write(figure.getBookmarkId(), "figure-" + figure.getOrdinal() + "/prompt.txt", prompt);
    }

    public void cached(Bookmark bookmark, BookmarkFigure figure, FetchedPage image, String knowledge) {
        image(bookmark, figure, image);
        FigureDebugOutcome outcome = outcome(figure, "cached", knowledge, image, null, null, null);
        updateManifest(bookmark, previous -> withOutcome(previous, outcome));
    }

    public void vision(Bookmark bookmark, BookmarkFigure figure, FetchedPage image, VisionResult result,
                       String model) {
        String status = result.isNone() ? "none" : "described";
        FigureDebugOutcome outcome = outcome(figure, status, result.knowledge(), image, model, result, null);
        updateManifest(bookmark, previous -> withOutcome(previous, outcome));
    }

    public void failure(Bookmark bookmark, BookmarkFigure figure, RuntimeException failure) {
        FigureDebugOutcome outcome = outcome(figure, "failed", null, null, null, null, failure);
        updateManifest(bookmark, previous -> withOutcome(previous, outcome));
    }

    public void response(BookmarkFigure figure, String response) {
        write(figure.getBookmarkId(), "figure-" + figure.getOrdinal() + "/response.txt", response);
    }

    public void error(BookmarkFigure figure, RuntimeException failure) {
        write(figure.getBookmarkId(), "figure-" + figure.getOrdinal() + "/response.txt",
                "ERROR: " + failure.getMessage());
    }

    private void image(Bookmark bookmark, BookmarkFigure figure, FetchedPage image) {
        String metadata = """
                articleUrl=%s
                figureUrl=%s
                ordinal=%d
                caption=%s
                altText=%s
                precedingParagraph=%s
                followingParagraph=%s
                mediaType=%s
                imageSha256=%s
                """.formatted(
                bookmark == null ? "unknown" : bookmark.getUrl(), figure.getImageUrl(), figure.getOrdinal(),
                figure.getCaption(), figure.getAltText(), figure.getPrecedingParagraph(),
                figure.getFollowingParagraph(), image.contentType(), sha256(image.body()));
        write(figure.getBookmarkId(), "figure-" + figure.getOrdinal() + "/metadata.txt", metadata);
        writeBytes(figure.getBookmarkId(), "figure-" + figure.getOrdinal() + "/image.png", image.body());
    }

    private static FigureDebugOutcome outcome(BookmarkFigure figure, String status, String knowledge,
            FetchedPage image, String model, VisionResult result, RuntimeException failure) {
        return new FigureDebugOutcome(figure.getOrdinal(), figure.getImageUrl(), figure.getAltText(),
                figure.getCaption(), figure.getPrecedingParagraph(), figure.getFollowingParagraph(), status,
                knowledge, image == null ? null : image.contentType().toString(),
                image == null ? null : sha256(image.body()), model,
                result == null ? null : result.promptTokens(),
                result == null ? null : result.completionTokens(),
                result == null ? null : result.totalTokens(),
                failure == null ? null : failure.getMessage());
    }

    private static FigureDebugManifest withOutcome(FigureDebugManifest previous, FigureDebugOutcome outcome) {
        List<FigureDebugOutcome> figures = new ArrayList<>(previous.figures());
        figures.removeIf(figure -> figure.ordinal() == outcome.ordinal());
        figures.add(outcome);
        figures.sort(Comparator.comparingInt(FigureDebugOutcome::ordinal));
        return copy(previous, previous.decisions(), figures, previous.insertions(), previous.reusedFromBookmark());
    }

    private static FigureDebugManifest copy(FigureDebugManifest previous, List<ExtractedFigureDecision> decisions,
            List<FigureDebugOutcome> figures, List<FigureInsertion> insertions, String reusedFrom) {
        return new FigureDebugManifest(MANIFEST_VERSION, previous.bookmarkId(), previous.articleUrl(),
                previous.title(), previous.startedAt(), previous.updatedAt(), decisions, figures, insertions,
                reusedFrom);
    }

    private synchronized void updateManifest(Bookmark bookmark, UnaryOperator<FigureDebugManifest> update) {
        if (!properties.enabled()) {
            return;
        }
        String bookmarkId = id(bookmark);
        try {
            Path path = path(bookmark.getId(), "run.json");
            FigureDebugManifest previous = Files.exists(path)
                    ? json.readValue(Files.readString(path), FigureDebugManifest.class) : null;
            FigureDebugManifest base = base(bookmark, bookmarkId, previous);
            FigureDebugManifest updated = update.apply(base);
            updated = new FigureDebugManifest(updated.schemaVersion(), updated.bookmarkId(), updated.articleUrl(),
                    updated.title(), updated.startedAt(), Instant.now().toString(), updated.decisions(),
                    updated.figures(), updated.insertions(), updated.reusedFromBookmark());
            Files.createDirectories(path.getParent());
            Files.writeString(path, json.writerWithDefaultPrettyPrinter().writeValueAsString(updated),
                    StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (Exception e) {
            // Local diagnostics must never change extraction, summarisation, or audio outcomes.
            log.warn("Could not write figure debug manifest for {}", bookmarkId, e);
        }
    }

    private static FigureDebugManifest base(Bookmark bookmark, String bookmarkId, FigureDebugManifest previous) {
        String now = Instant.now().toString();
        if (previous == null) {
            return new FigureDebugManifest(MANIFEST_VERSION, bookmarkId, bookmark.getUrl(), bookmark.getTitle(),
                    now, now, List.of(), List.of(), List.of(), null);
        }
        return new FigureDebugManifest(previous.schemaVersion(), bookmarkId, bookmark.getUrl(),
                bookmark.getTitle() == null ? previous.title() : bookmark.getTitle(), previous.startedAt(), now,
                previous.decisions(), previous.figures(), previous.insertions(), previous.reusedFromBookmark());
    }

    private static String id(Bookmark bookmark) {
        return bookmark.getId() == null ? "unknown-bookmark" : bookmark.getId().toString();
    }

    private void write(UUID bookmarkId, String relativePath, String content) {
        if (!properties.enabled()) {
            return;
        }
        try {
            Path path = path(bookmarkId, relativePath);
            Files.createDirectories(path.getParent());
            Files.writeString(path, content == null ? "" : content, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (Exception e) {
            // Local diagnostics must never change extraction, summarisation, or audio outcomes.
            log.warn("Could not write figure debug artifact {}", relativePath, e);
        }
    }

    private void writeBytes(UUID bookmarkId, String relativePath, byte[] bytes) {
        if (!properties.enabled()) {
            return;
        }
        try {
            Path path = path(bookmarkId, relativePath);
            Files.createDirectories(path.getParent());
            Files.write(path, bytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (Exception e) {
            log.warn("Could not write figure debug artifact {}", relativePath, e);
        }
    }

    private Path path(UUID bookmarkId, String relativePath) {
        return root.resolve(bookmarkId == null ? "unknown-bookmark" : bookmarkId.toString())
                .resolve(relativePath);
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 is required by the JDK", e);
        }
    }
}
