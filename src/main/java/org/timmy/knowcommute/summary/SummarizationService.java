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

    private final ChatClient.Builder chatClientBuilder;
    private final SummarizationProperties properties;
    private final Resource promptTemplate;

    public String summarise(String title, String sourceDomain, String article, SummaryLength length) {
        String input = truncateToBudget(article);

        String summary = chatClientBuilder.build().prompt()
                .user(spec -> spec.text(promptTemplate).params(Map.of(
                        "targetWords", String.valueOf(length.targetWords()),
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
