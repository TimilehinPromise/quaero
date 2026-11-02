package org.timmy.knowcommute.extraction;

import static org.timmy.knowcommute.extraction.ArticleText.countWords;
import static org.timmy.knowcommute.extraction.ArticleText.normalise;

import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.Elements;
import org.timmy.knowcommute.figure.FigureProperties;

@Slf4j
final class FigureMarkup {

    private static final Set<String> GENERIC_TEXT = Set.of(
            "image", "photo", "picture", "hero", "thumbnail", "banner", "figure", "diagram",
            "chart", "illustration", "icon", "logo");

    private static final Pattern MARKDOWN_IMAGE = Pattern.compile(
            "\\[!\\[([^\\]]*)\\]\\(([^)\\s]+)(?:\\s+[^)]*)?\\)\\]\\([^)]+\\)"
                    + "|!\\[([^\\]]*)\\]\\(([^)\\s]+)(?:\\s+[^)]*)?\\)");

    /**
     * A nearby paragraph that reads like a caption for an image with no figcaption, e.g.
     * "Figure 1: Generalized Ads Marketplace architecture." Catches the caption-in-prose
     * convention used by many engineering blogs.
     */
    private static final Pattern FIGURE_REFERENCE =
            Pattern.compile("^fig(?:ure)?\\.?\\s*\\d+\\s*[:.)]", Pattern.CASE_INSENSITIVE);

    /** URL smells that demote an image; ranking only, these never reject outright. */
    private static final Pattern JUNK_URL = Pattern.compile(
            "(?:^|[^a-z0-9])(?:logo|icon|avatar|badge|sprite|spacer|pixel|1x1)(?:[^a-z0-9]|$)");

    private static final double CAPTION_SCORE = 30;
    private static final double FIGURE_REFERENCE_SCORE = 20;
    private static final double FIGURE_WRAPPER_SCORE = 10;
    private static final double ALT_SCORE = 5;
    private static final double JUNK_URL_SCORE = -30;

    private FigureMarkup() {
    }

    static Result html(Element content, String articleTitle, FigureProperties properties) {
        Element proseOnly = content.clone();
        proseOnly.select("figure, img").remove();
        int wordCount = countWords(normalise(proseOnly.text()));
        Elements items = content.select("figure, img");
        Map<Element, Seen> seen = new IdentityHashMap<>();
        int imagesSeen = 0;

        for (Element item : items) {
            if ("img".equals(item.tagName()) && item.closest("figure") != null) {
                continue;
            }
            imagesSeen++;
            Element image = representativeImage(item);
            String caption = item.selectFirst("figcaption") == null
                    ? null : clean(item.selectFirst("figcaption").text());
            String alt = image == null ? null : clean(image.attr("alt"));
            boolean linked = item.closest("a") != null || (image != null && image.closest("a") != null);
            String linkedTarget = linked ? linkedImageTarget(item, image) : null;
            String imageUrl = linkedTarget != null ? linkedTarget : imageUrl(image, item);
            String markup = usableMarkup(caption, alt, articleTitle, imageUrl);
            String before = adjacentParagraph(item, content, true);
            String after = adjacentParagraph(item, content, false);
            // A link that points at an image file is a link TO the figure, not away from it:
            // editorial CMSs wrap a small in-body thumbnail in <a href="full-size.png">.
            // Follow it; the thumbnail's declared dimensions no longer describe what we fetch,
            // so only the href itself (validated above) gates acceptance. Lightbox links to
            // article pages stay rejected as linked.
            String rejection = linkedTarget != null
                    ? null
                    : rejectionReason(image, imageUrl, linked, properties.minDimension());
            double score = properties.enabled() && rejection == null
                    ? score(!"img".equals(item.tagName()), caption, alt, before, after, imageUrl,
                            articleTitle, imagesSeen)
                    : 0;
            seen.put(item, new Seen(imagesSeen, imageUrl, alt, caption, before, after, markup, rejection, score));
        }

        Map<Element, Integer> ordinals = selectOrdinals(seen, properties);
        List<ExtractedFigure> figures = new ArrayList<>();
        for (Element item : items) {
            Seen details = seen.get(item);
            if (details == null) {
                continue;
            }
            Integer ordinal = ordinals.get(item);
            String replacement;
            if (ordinal != null) {
                figures.add(new ExtractedFigure(ordinal, details.imageUrl(), details.alt(), details.caption(),
                        details.before(), details.after()));
                replacement = "[Figure " + ordinal + "]";
                log.info("Figure selected: ordinal={} url={} score={}", ordinal, details.imageUrl(), details.score());
            } else {
                replacement = details.markup() == null ? "" : "[Figure: " + details.markup() + "]";
                if (properties.enabled()) {
                    log.info("Figure skipped: url={} reason={}", details.imageUrl(), skipReason(details));
                }
            }
            item.replaceWith(new TextNode(" " + replacement + " "));
        }
        long candidates = seen.values().stream().filter(details -> details.rejection() == null).count();
        log.info("Figure markup extraction: route=html visionEnabled={} imagesSeen={} candidates={} selected={}",
                properties.enabled(), imagesSeen, candidates, figures.size());
        return new Result(normalise(content.text()), wordCount, figures);
    }

    static Result markdown(String markdown, URI baseUri, String articleTitle, FigureProperties properties) {
        Matcher matcher = MARKDOWN_IMAGE.matcher(markdown);
        List<SeenMatch> matches = new ArrayList<>();
        int imagesSeen = 0;

        while (matcher.find()) {
            imagesSeen++;
            boolean linked = matcher.group(1) != null;
            String alt = clean(linked ? matcher.group(1) : matcher.group(3));
            String imageUrl = absoluteUrl(linked ? matcher.group(2) : matcher.group(4), baseUri);
            String markup = usableMarkup(null, alt, articleTitle, imageUrl);
            String before = markdownParagraph(markdown, matcher.start(), true);
            String after = markdownParagraph(markdown, matcher.end(), false);
            String rejection = rejectionReason(imageUrl, linked);
            double score = properties.enabled() && rejection == null
                    ? score(false, null, alt, before, after, imageUrl, articleTitle, imagesSeen)
                    : 0;
            matches.add(new SeenMatch(matcher.start(), matcher.end(), imageUrl, alt, before, after,
                    markup, rejection, score));
        }

        Map<Integer, Integer> ordinals = selectMarkdownOrdinals(matches, properties);
        StringBuilder marked = new StringBuilder();
        StringBuilder prose = new StringBuilder();
        List<ExtractedFigure> figures = new ArrayList<>();
        int cursor = 0;
        for (int index = 0; index < matches.size(); index++) {
            SeenMatch details = matches.get(index);
            marked.append(markdown, cursor, details.start());
            prose.append(markdown, cursor, details.start());
            Integer ordinal = ordinals.get(index);
            String replacement;
            if (ordinal != null) {
                figures.add(new ExtractedFigure(ordinal, details.imageUrl(), details.alt(), null,
                        details.before(), details.after()));
                replacement = "[Figure " + ordinal + "]";
                log.info("Figure selected: ordinal={} url={} score={}", ordinal, details.imageUrl(), details.score());
            } else {
                replacement = details.markup() == null ? "" : "[Figure: " + details.markup() + "]";
                if (properties.enabled()) {
                    log.info("Figure skipped: url={} reason={}", details.imageUrl(), skipReason(details));
                }
            }
            marked.append(' ').append(replacement).append(' ');
            prose.append(' ');
            cursor = details.end();
        }
        marked.append(markdown, cursor, markdown.length());
        prose.append(markdown, cursor, markdown.length());
        long candidates = matches.stream().filter(details -> details.rejection() == null).count();
        log.info("Figure markup extraction: route=markdown visionEnabled={} imagesSeen={} candidates={} selected={}",
                properties.enabled(), imagesSeen, candidates, figures.size());
        return new Result(normalise(marked.toString()), countWords(normalise(prose.toString())), figures);
    }

    /**
     * Rank all valid candidates and keep the best {@code maxPerArticle}. Ranking decides who gets
     * a vision slot; it never removes an image from the text. Ordinals are assigned in document
     * order regardless of rank, because the "[Figure N]" placeholders are positional.
     */
    private static Map<Element, Integer> selectOrdinals(Map<Element, Seen> seen, FigureProperties properties) {
        if (!properties.enabled()) {
            return Map.of();
        }
        List<Map.Entry<Element, Seen>> ranked = seen.entrySet().stream()
                .filter(entry -> entry.getValue().rejection() == null)
                .sorted((left, right) -> Double.compare(right.getValue().score(), left.getValue().score()))
                .limit(properties.maxPerArticle())
                .toList();
        List<Map.Entry<Element, Seen>> documentOrder = ranked.stream()
                .sorted(Comparator.comparingInt(entry -> entry.getValue().position()))
                .toList();
        Map<Element, Integer> ordinals = new IdentityHashMap<>();
        for (int index = 0; index < documentOrder.size(); index++) {
            ordinals.put(documentOrder.get(index).getKey(), index + 1);
        }
        return ordinals;
    }

    private static Map<Integer, Integer> selectMarkdownOrdinals(List<SeenMatch> matches, FigureProperties properties) {
        if (!properties.enabled()) {
            return Map.of();
        }
        List<Integer> ranked = IntStream.range(0, matches.size())
                .filter(index -> matches.get(index).rejection() == null)
                .boxed()
                .sorted((left, right) -> Double.compare(matches.get(right).score(), matches.get(left).score()))
                .limit(properties.maxPerArticle())
                .sorted()
                .toList();
        Map<Integer, Integer> ordinals = new HashMap<>();
        for (int index = 0; index < ranked.size(); index++) {
            ordinals.put(ranked.get(index), index + 1);
        }
        return ordinals;
    }

    /**
     * Preference score for who gets a vision slot. Every term is evidence-backed: captions and
     * caption-like adjacent paragraphs track useful figures, alt-text richness does not (decorative
     * heroes carry rich alt too), so alt is a small tiebreak. Document position is the final
     * tiebreak, which makes the sort fully deterministic and keeps FIFO behaviour on equal images.
     */
    private static double score(boolean wrappedInFigure, String caption, String alt, String before,
            String after, String imageUrl, String articleTitle, int position) {
        double score = -0.01 * position;
        if (meaningful(caption, articleTitle, imageUrl) != null) {
            score += CAPTION_SCORE;
        }
        if (figureReference(before) || figureReference(after)) {
            score += FIGURE_REFERENCE_SCORE;
        }
        if (wrappedInFigure) {
            score += FIGURE_WRAPPER_SCORE;
        }
        if (meaningful(alt, articleTitle, imageUrl) != null) {
            score += ALT_SCORE;
        }
        if (imageUrl != null && JUNK_URL.matcher(imageUrl.toLowerCase(Locale.ROOT)).find()) {
            score += JUNK_URL_SCORE;
        }
        return score;
    }

    private static boolean figureReference(String paragraph) {
        return paragraph != null && FIGURE_REFERENCE.matcher(paragraph.trim()).find();
    }

    private static String skipReason(Seen details) {
        return details.rejection() == null
                ? "ranked-below-cut score=" + details.score()
                : details.rejection();
    }

    private static String skipReason(SeenMatch details) {
        return details.rejection() == null
                ? "ranked-below-cut score=" + details.score()
                : details.rejection();
    }

    private static String rejectionReason(Element image, String imageUrl, boolean linked, int minDimension) {
        if (image == null) {
            return "no-image";
        }
        if (image.attr("src").startsWith("data:")) {
            return "data-uri";
        }
        String rejection = rejectionReason(imageUrl, linked);
        if (rejection != null) {
            return rejection;
        }
        return dimensionRejection(image, minDimension);
    }

    private static String dimensionRejection(Element image, int minDimension) {
        int width = image == null ? -1 : dimension(image.attr("width"));
        int height = image == null ? -1 : dimension(image.attr("height"));
        if (width >= 0 && width < minDimension) {
            return "width-below-" + minDimension;
        }
        if (height >= 0 && height < minDimension) {
            return "height-below-" + minDimension;
        }
        return null;
    }

    /**
     * When an image is wrapped in a link, the link target decides whether the link is a
     * liability or an asset: a href that is itself an image file means the CMS linked the
     * in-body thumbnail to its full-size original. Lightbox links back to the article page
     * return null and the image stays rejected as linked.
     */
    private static String linkedImageTarget(Element item, Element image) {
        Element anchor = item.closest("a");
        if (anchor == null && image != null) {
            anchor = image.closest("a");
        }
        if (anchor == null) {
            return null;
        }
        String href = anchor.absUrl("href");
        try {
            URI uri = URI.create(href);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            String path = uri.getPath() == null ? "" : uri.getPath().toLowerCase(Locale.ROOT);
            if (!Set.of("http", "https").contains(scheme)
                    || !path.matches(".*\\.(png|jpe?g|gif|webp|svgz?|avif|bmp)$")) {
                return null;
            }
            return href;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String rejectionReason(String imageUrl, boolean linked) {
        if (linked) {
            return "linked-image";
        }
        if (imageUrl == null) {
            return "missing-or-unsupported-url";
        }
        try {
            URI uri = URI.create(imageUrl);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            String path = uri.getPath() == null ? "" : uri.getPath().toLowerCase(Locale.ROOT);
            if (!Set.of("http", "https").contains(scheme)) {
                return "unsupported-url-scheme";
            }
            if (path.endsWith(".svgz")) {
                return "compressed-svg-url";
            }
            return null;
        } catch (IllegalArgumentException e) {
            return "malformed-url";
        }
    }

    /**
     * The image that represents a figure. Lazy-loading markup often parks a 1x1 data-URI
     * placeholder img ahead of the real one inside the same figure; judging the figure by the
     * placeholder rejects it as a data-URI while the real image is skipped as nested. Prefer the
     * first fetchable src, largest declared size first, and only fall back to a placeholder when
     * nothing else is there.
     */
    private static Element representativeImage(Element item) {
        if ("img".equals(item.tagName())) {
            return item;
        }
        List<Element> images = item.select("img");
        if (images.isEmpty()) {
            return null;
        }
        return images.stream()
                .filter(image -> !image.attr("src").startsWith("data:"))
                .max(Comparator.comparingLong(FigureMarkup::imageArea))
                .orElse(images.get(0));
    }

    private static long imageArea(Element image) {
        int width = dimension(image.attr("width"));
        int height = dimension(image.attr("height"));
        return (width < 0 || height < 0) ? 0 : (long) width * height;
    }

    private static int dimension(String value) {
        if (value == null || !value.matches("\\s*\\d+.*")) {
            return -1;
        }
        try {
            return Integer.parseInt(value.trim().replaceFirst("^(\\d+).*$", "$1"));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static String usableMarkup(String caption, String alt, String articleTitle, String imageUrl) {
        String usefulCaption = meaningful(caption, articleTitle, imageUrl);
        String usefulAlt = meaningful(alt, articleTitle, imageUrl);
        if (usefulCaption == null) {
            return usefulAlt;
        }
        if (usefulAlt != null && !usefulAlt.equalsIgnoreCase(usefulCaption)) {
            return usefulCaption + " " + usefulAlt;
        }
        return usefulCaption;
    }

    private static String meaningful(String value, String articleTitle, String imageUrl) {
        if (value == null || countWords(value) <= 2
                || GENERIC_TEXT.contains(value.toLowerCase(Locale.ROOT))) {
            return null;
        }
        if (articleTitle != null && value.equalsIgnoreCase(normalise(articleTitle))) {
            return null;
        }
        return value.equalsIgnoreCase(filename(imageUrl)) ? null : value;
    }

    private static String filename(String imageUrl) {
        if (imageUrl == null) {
            return "";
        }
        try {
            String path = URI.create(imageUrl).getPath();
            if (path == null) {
                return "";
            }
            int slash = path.lastIndexOf('/');
            return slash < 0 ? path : path.substring(slash + 1);
        } catch (IllegalArgumentException e) {
            return "";
        }
    }

    /**
     * The fetchable URL for an image. Responsive markup often leaves {@code img} without a src
     * and puts the only copy of the URL in a srcset on the img or a sibling source element;
     * without this fallback those images die as missing-or-unsupported-url. The widest candidate
     * wins, so vision gets the best quality the page offers.
     */
    private static String imageUrl(Element image, Element item) {
        if (image != null) {
            String absolute = image.absUrl("src");
            if (!absolute.isBlank()) {
                return absolute;
            }
            String fromSrcset = largestSrcsetUrl(image.attr("srcset"), image.baseUri());
            if (fromSrcset != null) {
                return fromSrcset;
            }
        }
        for (Element source : item.select("source[srcset]")) {
            String fromSrcset = largestSrcsetUrl(source.attr("srcset"), source.baseUri());
            if (fromSrcset != null) {
                return fromSrcset;
            }
        }
        return null;
    }

    private static String largestSrcsetUrl(String srcset, String baseUri) {
        if (srcset == null || srcset.isBlank()) {
            return null;
        }
        String best = null;
        int bestWidth = -1;
        for (String candidate : srcset.split(",")) {
            String[] parts = candidate.trim().split("\\s+");
            if (parts.length == 0 || parts[0].isEmpty()) {
                continue;
            }
            int width = parts.length > 1 && parts[1].endsWith("w")
                    ? dimension(parts[1].substring(0, parts[1].length() - 1))
                    : 0;
            String resolved = absoluteUrl(parts[0], URI.create(baseUri));
            if (resolved != null && width >= bestWidth) {
                best = resolved;
                bestWidth = width;
            }
        }
        return best;
    }

    private static String absoluteUrl(String source, Element image) {
        String absolute = image.absUrl("src");
        return absolute.isBlank() ? absoluteUrl(source, URI.create(image.baseUri())) : absolute;
    }

    private static String absoluteUrl(String source, URI baseUri) {
        if (source == null || source.isBlank() || source.startsWith("data:")) {
            return null;
        }
        try {
            return baseUri.resolve(source).toString();
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String adjacentParagraph(Element item, Element content, boolean preceding) {
        Element current = item;
        while (current != null && current != content) {
            Element sibling = preceding ? current.previousElementSibling() : current.nextElementSibling();
            while (sibling != null) {
                Element paragraph = "p".equals(sibling.tagName()) ? sibling : sibling.select("p").stream()
                        .reduce((first, second) -> preceding ? second : first).orElse(null);
                if (paragraph != null && !normalise(paragraph.text()).isEmpty()) {
                    return normalise(paragraph.text());
                }
                sibling = preceding ? sibling.previousElementSibling() : sibling.nextElementSibling();
            }
            current = current.parent();
        }
        return null;
    }

    private static String markdownParagraph(String markdown, int offset, boolean preceding) {
        String region = preceding ? markdown.substring(0, offset) : markdown.substring(offset);
        String[] blocks = region.split("\\n\\s*\\n");
        for (int index = preceding ? blocks.length - 1 : 0;
                index >= 0 && index < blocks.length; index += preceding ? -1 : 1) {
            String block = normalise(blocks[index]);
            if (!block.isEmpty()) {
                return block;
            }
        }
        return null;
    }

    private static String clean(String value) {
        String cleaned = normalise(value);
        return cleaned == null || cleaned.isEmpty() ? null : cleaned;
    }

    private record Seen(int position, String imageUrl, String alt, String caption, String before,
            String after, String markup, String rejection, double score) {
    }

    private record SeenMatch(int start, int end, String imageUrl, String alt, String before,
            String after, String markup, String rejection, double score) {
    }

    record Result(String text, int wordCount, List<ExtractedFigure> figures) {
        Result {
            figures = List.copyOf(figures);
        }
    }
}
