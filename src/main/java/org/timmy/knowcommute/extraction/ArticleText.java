package org.timmy.knowcommute.extraction;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Shared by every {@link DocumentExtractor} so two formats cannot disagree on a content hash. */
final class ArticleText {

    private ArticleText() {
    }

    static String normalise(String text) {
        return text == null ? null : text.replaceAll("\\s+", " ").trim();
    }

    static int countWords(String text) {
        String trimmed = text.trim();
        return trimmed.isEmpty() ? 0 : trimmed.split("\\s+").length;
    }

    static String sourceDomain(URI url) {
        String host = url.getHost();
        return host == null ? "" : host.replaceFirst("^www\\.", "");
    }

    static String sha256(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by the JDK", e);
        }
    }
}
