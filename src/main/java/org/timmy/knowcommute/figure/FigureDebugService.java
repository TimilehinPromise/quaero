package org.timmy.knowcommute.figure;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.timmy.knowcommute.bookmark.Bookmark;
import org.timmy.knowcommute.extraction.FetchedPage;

@Slf4j
@Service
public class FigureDebugService {

    private final FigureDebugProperties properties;
    private final Path root;

    public FigureDebugService(FigureDebugProperties properties) {
        this.properties = properties;
        this.root = properties.enabled() ? Path.of(properties.directory()) : null;
    }

    public void article(Bookmark bookmark, String article) {
        write(bookmark.getId(), "article.txt", article);
    }

    public void enriched(Bookmark bookmark, String article) {
        write(bookmark.getId(), "enriched-article.txt", article);
    }

    public void summary(Bookmark bookmark, String summary, UUID reusedFrom) {
        String provenance = reusedFrom == null ? "" : "reusedFromBookmark=" + reusedFrom + "\n\n";
        write(bookmark.getId(), "summary.txt", provenance + summary);
    }

    public void request(Bookmark bookmark, BookmarkFigure figure, FetchedPage image, String prompt) {
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
        write(figure.getBookmarkId(), "figure-" + figure.getOrdinal() + "/prompt.txt", prompt);
    }

    public void response(BookmarkFigure figure, String response) {
        write(figure.getBookmarkId(), "figure-" + figure.getOrdinal() + "/response.txt", response);
    }

    public void error(BookmarkFigure figure, RuntimeException failure) {
        write(figure.getBookmarkId(), "figure-" + figure.getOrdinal() + "/response.txt",
                "ERROR: " + failure.getMessage());
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
