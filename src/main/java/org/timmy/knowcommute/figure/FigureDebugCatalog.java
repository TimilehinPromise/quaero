package org.timmy.knowcommute.figure;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.timmy.knowcommute.extraction.ExtractedFigureDecision;

@Slf4j
@Service
@ConditionalOnProperty(prefix = "readcast.figures.debug", name = "viewer-enabled", havingValue = "true")
public class FigureDebugCatalog {

    private static final int CONTEXT_CHARACTERS = 180;

    private final Path root;
    private final ObjectMapper json = new ObjectMapper();

    public FigureDebugCatalog(FigureDebugProperties properties) {
        this.root = properties.enabled() ? Path.of(properties.directory()).toAbsolutePath().normalize() : null;
    }

    public List<FigureDebugRunSummary> listRuns() {
        if (root == null || !Files.isDirectory(root)) {
            return List.of();
        }
        try (var paths = Files.list(root)) {
            return paths.filter(Files::isDirectory)
                    .map(this::summary)
                    .sorted(Comparator.comparing(FigureDebugRunSummary::updatedAt).reversed())
                    .toList();
        } catch (IOException e) {
            log.warn("Could not list figure debug runs from {}", root, e);
            return List.of();
        }
    }

    public Optional<FigureDebugRunDetail> findRun(String id) {
        Path run = run(id);
        return run == null ? Optional.empty() : Optional.of(detail(run));
    }

    public Optional<FigureDebugImage> findImage(String id, int ordinal) {
        Path run = run(id);
        if (run == null || ordinal < 1) {
            return Optional.empty();
        }
        Path directory = run.resolve("figure-" + ordinal).normalize();
        Path image = directory.resolve("image.png").normalize();
        if (!directory.startsWith(run) || !image.startsWith(directory) || !Files.isRegularFile(image)) {
            return Optional.empty();
        }
        String mediaType = value(metadata(directory), "mediaType");
        return Optional.of(new FigureDebugImage(image,
                mediaType == null ? "application/octet-stream" : mediaType));
    }

    private FigureDebugRunSummary summary(Path run) {
        FigureDebugManifest manifest = manifest(run);
        List<FigureDebugRunDetail.Figure> figures = figures(run, manifest);
        String reused = manifest != null ? manifest.reusedFromBookmark() : summaryContent(run).reusedFrom();
        return new FigureDebugRunSummary(run.getFileName().toString(), title(run, manifest), articleUrl(run, manifest),
                updatedAt(run, manifest), figures.size(),
                (int) figures.stream().filter(figure -> figure.knowledge() != null).count(),
                (int) figures.stream().filter(FigureDebugCatalog::isNone).count(),
                (int) figures.stream().filter(figure -> "failed".equals(figure.status())).count(),
                (int) figures.stream().filter(figure -> "cached".equals(figure.status())).count(),
                reused != null, manifest != null);
    }

    private FigureDebugRunDetail detail(Path run) {
        FigureDebugManifest manifest = manifest(run);
        List<FigureDebugRunDetail.Figure> figures = figures(run, manifest);
        String original = text(run.resolve("article.txt"));
        String enriched = text(run.resolve("enriched-article.txt"));
        SummaryContent summary = summaryContent(run);
        String reused = manifest != null && manifest.reusedFromBookmark() != null
                ? manifest.reusedFromBookmark() : summary.reusedFrom();
        List<ExtractedFigureDecision> decisions = manifest == null ? List.of() : manifest.decisions();
        List<FigureInsertion> insertions = manifest == null
                ? approximateInsertions(enriched, figures) : manifest.insertions();
        return new FigureDebugRunDetail(run.getFileName().toString(), title(run, manifest), articleUrl(run, manifest),
                updatedAt(run, manifest), value(original), value(enriched), summary.text(), wordCount(original),
                wordCount(enriched), wordCount(summary.text()), reused, decisions, figures, insertions);
    }

    private List<FigureDebugRunDetail.Figure> figures(Path run, FigureDebugManifest manifest) {
        return manifest == null ? legacyFigures(run) : manifestFigures(run, manifest);
    }

    private List<FigureDebugRunDetail.Figure> manifestFigures(Path run, FigureDebugManifest manifest) {
        Map<Integer, ExtractedFigureDecision> selected = manifest.decisions().stream()
                .filter(decision -> decision.ordinal() != null)
                .collect(Collectors.toMap(ExtractedFigureDecision::ordinal, Function.identity(), (left, right) -> left));
        Map<Integer, FigureDebugOutcome> outcomes = manifest.figures().stream()
                .collect(Collectors.toMap(FigureDebugOutcome::ordinal, Function.identity(), (left, right) -> right));
        List<Integer> ordinals = new ArrayList<>(selected.keySet());
        for (Integer ordinal : outcomes.keySet()) {
            if (!selected.containsKey(ordinal)) {
                ordinals.add(ordinal);
            }
        }
        ordinals.sort(Integer::compareTo);
        return ordinals.stream().map(ordinal -> manifestFigure(run, ordinal, selected.get(ordinal),
                outcomes.get(ordinal))).toList();
    }

    private FigureDebugRunDetail.Figure manifestFigure(Path run, int ordinal, ExtractedFigureDecision decision,
            FigureDebugOutcome outcome) {
        Path directory = run.resolve("figure-" + ordinal);
        Map<String, String> metadata = metadata(directory);
        String status = outcome == null ? "not-captured" : outcome.status();
        return new FigureDebugRunDetail.Figure(ordinal,
                outcome != null ? outcome.imageUrl() : decision == null ? value(metadata, "figureUrl") : decision.imageUrl(),
                outcome != null ? outcome.altText() : decision == null ? value(metadata, "altText") : decision.altText(),
                outcome != null ? outcome.caption() : decision == null ? value(metadata, "caption") : decision.caption(),
                outcome != null ? outcome.precedingParagraph()
                        : decision == null ? value(metadata, "precedingParagraph") : decision.precedingParagraph(),
                outcome != null ? outcome.followingParagraph()
                        : decision == null ? value(metadata, "followingParagraph") : decision.followingParagraph(),
                status, outcome == null ? null : outcome.knowledge(),
                outcome != null && outcome.mediaType() != null ? outcome.mediaType() : value(metadata, "mediaType"),
                outcome != null && outcome.imageSha256() != null
                        ? outcome.imageSha256() : value(metadata, "imageSha256"),
                outcome == null ? null : outcome.model(), outcome == null ? null : outcome.promptTokens(),
                outcome == null ? null : outcome.completionTokens(), outcome == null ? null : outcome.totalTokens(),
                outcome == null ? null : outcome.error(), text(directory.resolve("prompt.txt")),
                text(directory.resolve("response.txt")), Files.isRegularFile(directory.resolve("image.png")));
    }

    private List<FigureDebugRunDetail.Figure> legacyFigures(Path run) {
        if (!Files.isDirectory(run)) {
            return List.of();
        }
        try (var paths = Files.list(run)) {
            return paths.filter(Files::isDirectory)
                    .filter(path -> path.getFileName().toString().matches("figure-\\d+"))
                    .sorted(Comparator.comparingInt(path -> ordinal(path.getFileName().toString())))
                    .map(this::legacyFigure)
                    .toList();
        } catch (IOException e) {
            log.warn("Could not list figure debug artifacts in {}", run, e);
            return List.of();
        }
    }

    private FigureDebugRunDetail.Figure legacyFigure(Path directory) {
        int ordinal = ordinal(directory.getFileName().toString());
        Map<String, String> metadata = metadata(directory);
        String prompt = text(directory.resolve("prompt.txt"));
        String response = text(directory.resolve("response.txt"));
        String trimmed = response == null ? null : response.trim();
        String status = trimmed == null || trimmed.isEmpty() ? "not-captured"
                : trimmed.startsWith("ERROR:") ? "failed" : "NONE".equalsIgnoreCase(trimmed) ? "none" : "described";
        String knowledge = "described".equals(status) ? trimmed : null;
        String error = "failed".equals(status) ? trimmed.substring("ERROR:".length()).trim() : null;
        return new FigureDebugRunDetail.Figure(ordinal, value(metadata, "figureUrl"), value(metadata, "altText"),
                value(metadata, "caption"), value(metadata, "precedingParagraph"),
                value(metadata, "followingParagraph"), status, knowledge, value(metadata, "mediaType"),
                value(metadata, "imageSha256"), null, null, null, null, error, prompt, response,
                Files.isRegularFile(directory.resolve("image.png")));
    }

    private FigureDebugManifest manifest(Path run) {
        Path path = run.resolve("run.json");
        if (!Files.isRegularFile(path)) {
            return null;
        }
        try {
            return json.readValue(Files.readString(path), FigureDebugManifest.class);
        } catch (IOException | RuntimeException e) {
            log.warn("Could not read figure debug manifest {}; using legacy artifacts", path, e);
            return null;
        }
    }

    private String title(Path run, FigureDebugManifest manifest) {
        if (manifest != null && !isBlank(manifest.title())) {
            return manifest.title();
        }
        for (FigureDebugRunDetail.Figure figure : legacyFigures(run)) {
            String title = promptValue(figure.prompt(), "Article title: ");
            if (title != null) {
                return title;
            }
        }
        return null;
    }

    private String articleUrl(Path run, FigureDebugManifest manifest) {
        if (manifest != null && !isBlank(manifest.articleUrl())) {
            return manifest.articleUrl();
        }
        for (FigureDebugRunDetail.Figure figure : legacyFigures(run)) {
            Path figureDirectory = run.resolve("figure-" + figure.ordinal());
            String url = value(metadata(figureDirectory), "articleUrl");
            if (url != null) {
                return url;
            }
        }
        return null;
    }

    private static String updatedAt(Path run, FigureDebugManifest manifest) {
        if (manifest != null && manifest.updatedAt() != null) {
            return manifest.updatedAt();
        }
        try {
            return Files.getLastModifiedTime(run).toInstant().toString();
        } catch (IOException e) {
            return Instant.EPOCH.toString();
        }
    }

    private Path run(String id) {
        if (root == null || id == null) {
            return null;
        }
        try {
            if (!"unknown-bookmark".equals(id)) {
                UUID.fromString(id);
            }
        } catch (IllegalArgumentException e) {
            return null;
        }
        Path run = root.resolve(id).normalize();
        return run.startsWith(root) && Files.isDirectory(run) ? run : null;
    }

    private SummaryContent summaryContent(Path run) {
        String content = text(run.resolve("summary.txt"));
        if (content == null || !content.startsWith("reusedFromBookmark=")) {
            return new SummaryContent(content, null);
        }
        int lineEnd = content.indexOf('\n');
        int bodyStart = content.indexOf("\n\n");
        String reused = lineEnd < 0 ? null : content.substring("reusedFromBookmark=".length(), lineEnd);
        return new SummaryContent(bodyStart < 0 ? "" : content.substring(bodyStart + 2), reused);
    }

    private static List<FigureInsertion> approximateInsertions(String enriched,
            List<FigureDebugRunDetail.Figure> figures) {
        List<FigureInsertion> insertions = new ArrayList<>();
        int cursor = 0;
        for (FigureDebugRunDetail.Figure figure : figures) {
            if (enriched == null || figure.knowledge() == null || figure.knowledge().isBlank()) {
                insertions.add(FigureInsertion.notInserted(figure.ordinal()));
                continue;
            }
            String inserted = figure.knowledge().replaceAll("\\s+", " ").trim();
            int start = enriched.indexOf(inserted, cursor);
            if (start < 0) {
                insertions.add(FigureInsertion.notInserted(figure.ordinal()));
                continue;
            }
            int end = start + inserted.length();
            insertions.add(FigureInsertion.inserted(figure.ordinal(), inserted, start, end,
                    enriched.substring(Math.max(0, start - CONTEXT_CHARACTERS), start),
                    enriched.substring(end, Math.min(enriched.length(), end + CONTEXT_CHARACTERS))));
            cursor = end;
        }
        return insertions;
    }

    private Map<String, String> metadata(Path directory) {
        Map<String, String> values = new HashMap<>();
        String content = text(directory.resolve("metadata.txt"));
        if (content == null) {
            return values;
        }
        for (String line : content.split("\\R")) {
            int separator = line.indexOf('=');
            if (separator > 0) {
                values.put(line.substring(0, separator), line.substring(separator + 1));
            }
        }
        return values;
    }

    private String text(Path path) {
        try {
            return Files.isRegularFile(path) ? Files.readString(path) : null;
        } catch (IOException e) {
            log.warn("Could not read figure debug artifact {}", path, e);
            return null;
        }
    }

    private static String promptValue(String prompt, String prefix) {
        if (prompt == null) {
            return null;
        }
        for (String line : prompt.split("\\R")) {
            if (line.startsWith(prefix)) {
                return value(line.substring(prefix.length()));
            }
        }
        return null;
    }

    private static int ordinal(String directory) {
        return Integer.parseInt(directory.substring("figure-".length()));
    }

    private static boolean isNone(FigureDebugRunDetail.Figure figure) {
        return "none".equals(figure.status()) || "cached".equals(figure.status()) && figure.knowledge() == null;
    }

    private static int wordCount(String text) {
        return isBlank(text) ? 0 : text.trim().split("\\s+").length;
    }

    private static String value(Map<String, String> values, String key) {
        return value(values.get(key));
    }

    private static String value(String value) {
        return isBlank(value) || "null".equals(value) ? null : value;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record SummaryContent(String text, String reusedFrom) {
    }
}
