package org.timmy.knowcommute.summary;

import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.timmy.knowcommute.bookmark.SummaryLength;

@Slf4j
@Service
@RequiredArgsConstructor
public class SummarizationService {

    /**
     * A model cannot count its own words, so the prompt's ceiling is only advisory. Overshoot
     * within this factor is accepted — tighter and normal variation trips the retry constantly,
     * looser and a MEDIUM episode silently runs long.
     */
    private static final double CEILING_TOLERANCE = 1.30;

    private final ChatClient.Builder chatClientBuilder;
    private final SummarizationProperties properties;
    private final Resource promptTemplate;

    public String summarise(String title, String sourceDomain, String article, SummaryLength length) {
        String input = truncateToBudget(article);

        String summary = generate(title, sourceDomain, input, length);
        if (exceedsCeiling(summary, length)) {
            log.warn("Summary ran {} words against the {}-word ceiling; asking the model to tighten it",
                    wordCount(summary), length.targetWords());
            summary = tighten(summary, length);
        }
        if (exceedsCeiling(summary, length)) {
            log.warn("Tightened summary is still over the {}-word ceiling; truncating at a sentence boundary",
                    length.targetWords());
            summary = truncateAtSentenceBoundary(summary, length.targetWords());
        }
        return summary.trim();
    }

    /** True when a summary — fresh from the model or reused from another bookmark — is over the tolerated ceiling. */
    public boolean exceedsCeiling(String summary, SummaryLength length) {
        return wordCount(summary) > allowedWords(length);
    }

    private static int allowedWords(SummaryLength length) {
        return (int) (length.targetWords() * CEILING_TOLERANCE);
    }

    private String generate(String title, String sourceDomain, String input, SummaryLength length) {
        String summary = chatClientBuilder.build().prompt()
                .user(spec -> spec.text(promptTemplate).params(Map.of(
                        "targetWords", String.valueOf(length.targetWords()),
                        "paragraphs", String.valueOf(Math.max(length.targetWords() / 110, 2)),
                        "title", title == null ? "Untitled" : title,
                        "sourceDomain", sourceDomain == null ? "unknown" : sourceDomain,
                        "article", input)))
                .call()
                .content();

        if (summary == null || summary.isBlank()) {
            throw new SummarizationException("Model returned an empty summary");
        }
        return summary.trim();
    }

    /**
     * A model that overshot gets one explicit correction: the over-long text, its actual word
     * count, and the ceiling restated. Cheaper than resending the article, and the ask —
     * compress what you already wrote — is the same either way.
     */
    private String tighten(String summary, SummaryLength length) {
        String tightened = chatClientBuilder.build().prompt()
                .user("""
                        This narration is %d words — over the %d-word ceiling.

                        %s

                        Rewrite it at or under %d words. Keep every rule: spoken narration, no
                        headings or markdown, no preamble, output only the narration.
                        """.formatted(wordCount(summary), length.targetWords(), summary, length.targetWords()))
                .call()
                .content();

        if (tightened == null || tightened.isBlank()) {
            throw new SummarizationException("Model returned an empty summary");
        }
        return tightened.trim();
    }

    /**
     * Last resort, reachable only when the model ignores the correction: keep whole sentences
     * until the next one would cross the ceiling, so the narration never ends mid-thought.
     */
    private static String truncateAtSentenceBoundary(String summary, int maxWords) {
        StringBuilder kept = new StringBuilder();
        int words = 0;
        for (String sentence : summary.trim().split("(?<=[.!?])\\s+")) {
            int sentenceWords = wordCount(sentence);
            if (words > 0 && words + sentenceWords > maxWords) {
                break;
            }
            kept.append(sentence).append(' ');
            words += sentenceWords;
        }
        return kept.toString().trim();
    }

    private static int wordCount(String text) {
        return text.trim().split("\\s+").length;
    }

    /**
     * Guards the context window on very long articles. Truncating and telling the user nothing
     * beats failing the bookmark outright, but it is lossy, so it is logged.
     */
    private String truncateToBudget(String article) {
        String[] words = article.trim().split("\\s+");
        if (words.length <= properties.maxInputWords()) {
            return article;
        }
        log.warn("Truncating article from {} to {} words before summarising",
                words.length, properties.maxInputWords());
        return String.join(" ", java.util.Arrays.copyOf(words, properties.maxInputWords()));
    }
}
