package org.timmy.knowcommute.summary;

import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.openai.OpenAiChatOptions;
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
        return summarise(title, sourceDomain, article, length, ListenerNotes.NONE);
    }

    /**
     * The takeaway's words come out of the narration's budget, and it is appended only after
     * tightening and truncation, which see the narration alone — so it is never the part cut.
     */
    public String summarise(String title, String sourceDomain, String article, SummaryLength length,
                            ListenerNotes notes) {
        String input = modelInput(article);
        int inputWords = wordCount(input);

        SummaryTarget full = targetFor(length, inputWords);
        if (full.targetWords() < length.targetWords()) {
            log.info("{} asks for {} words but the article is {} words; aiming for {}",
                    length, length.targetWords(), inputWords, full.targetWords());
        }
        SummaryTarget target = full.reserving(notes.takeawayWords());

        String summary = generate(title, sourceDomain, input, target, notes);
        if (exceedsCeiling(summary, target)) {
            log.warn("Summary ran {} words against the {}-word ceiling; asking the model to tighten it",
                    wordCount(summary), target.targetWords());
            summary = tighten(summary, target);
        }
        if (exceedsCeiling(summary, target)) {
            log.warn("Tightened summary is still over the {}-word ceiling; truncating at a sentence boundary",
                    target.targetWords());
            summary = truncateAtSentenceBoundary(summary, target.targetWords());
        }
        return notes.appendTo(summary.trim());
    }

    /**
     * True when a summary — fresh from the model or reused from another bookmark — is over the
     * tolerated ceiling for this article. The article is needed because the ceiling depends on
     * it: a cached 1,500-word narration of a short piece is over-long now even though it was
     * within LONG when it was written.
     */
    public boolean exceedsCeiling(String summary, SummaryLength length, String article) {
        return exceedsCeiling(summary, targetFor(length, inputWordCount(article)));
    }

    private boolean exceedsCeiling(String summary, SummaryTarget target) {
        return wordCount(summary) > target.allowedWords();
    }

    private SummaryTarget targetFor(SummaryLength length, int inputWords) {
        return SummaryTarget.forArticle(length, inputWords,
                properties.maxSourceFraction(), properties.minWords());
    }

    /**
     * How much article the model will actually be given. The cap has to follow that rather than
     * the extracted length: an article over the input budget is truncated before it is
     * summarised, and a target derived from words the model never sees asks for a summary its
     * input cannot support. With the shipped budget the requested length always binds first, so
     * this only bites if {@code max-input-words} drops below about 4,500.
     */
    private int inputWordCount(String article) {
        return Math.min(wordCount(article), properties.maxInputWords());
    }

    private String generate(String title, String sourceDomain, String input, SummaryTarget target,
                            ListenerNotes notes) {
        ChatResponse response = chatClientBuilder.build().prompt()
                .options(timeoutOption())
                .user(spec -> spec.text(promptTemplate).params(Map.of(
                        "targetWords", String.valueOf(target.targetWords()),
                        "paragraphs", String.valueOf(target.paragraphs()),
                        "title", title == null ? "Untitled" : title,
                        "sourceDomain", sourceDomain == null ? "unknown" : sourceDomain,
                        "listenerNotes", notes.promptBlock(),
                        "article", input)))
                .call()
                .chatResponse();

        if (hitTokenLimit(response)) {
            throw new SummarizationException(
                    "Model hit the output token limit before finishing the summary");
        }
        String summary = textOf(response);
        if (summary == null || summary.isBlank()) {
            throw new SummarizationException("Model returned an empty summary");
        }
        return summary.trim();
    }

    /**
     * A model that overshot gets one explicit correction: the over-long text, its actual word
     * count, and the ceiling restated. Cheaper than resending the article, and the ask —
     * compress what you already wrote — is the same either way.
     *
     * <p>An empty reply keeps the draft rather than failing the step. The draft is a finished
     * narration that only needs cutting, and the sentence-boundary cut after this does that;
     * failing here threw it away and paid for the whole summary again on the retry.
     */
    private String tighten(String summary, SummaryTarget target) {
        ChatResponse response = chatClientBuilder.build().prompt()
                .options(timeoutOption())
                .user("""
                        This narration is %d words — over the %d-word ceiling.

                        %s

                        Rewrite it at or under %d words. Keep every rule: spoken narration, no
                        headings or markdown, no preamble, output only the narration.
                        """.formatted(wordCount(summary), target.targetWords(), summary, target.targetWords()))
                .call()
                .chatResponse();

        if (hitTokenLimit(response)) {
            log.warn("Tightening hit the output token limit; keeping the draft");
            return summary;
        }
        String tightened = textOf(response);
        if (tightened == null || tightened.isBlank()) {
            log.warn("Model returned an empty tightening; keeping the draft");
            return summary;
        }
        return tightened.trim();
    }

    /**
     * A reasoning model spends output tokens thinking before it writes, and when the limit runs
     * out mid-narration the reply still arrives as ordinary text — a summary that stops
     * mid-sentence, or nothing at all. The finish reason is the only signal that it was cut off.
     * LENGTH is the OpenAI-compatible name, MAX_TOKENS Anthropic's.
     */
    private static boolean hitTokenLimit(ChatResponse response) {
        String finishReason = response == null || response.getResult() == null
                ? null
                : response.getResult().getMetadata().getFinishReason();
        return "LENGTH".equalsIgnoreCase(finishReason) || "MAX_TOKENS".equalsIgnoreCase(finishReason);
    }

    private static String textOf(ChatResponse response) {
        return response == null || response.getResult() == null
                ? null
                : response.getResult().getOutput().getText();
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

    /**
     * The timeout rides on the call because it cannot be configured anywhere else. Spring AI
     * 2.0.1 drops both {@code spring.ai.openai.chat.timeout} and
     * {@code spring.ai.openai.chat.options.timeout} before they reach the client, so
     * openai-java's 60s default stands — and a long summary generates for longer than that,
     * which arrives as an HTTP/2 stream reset rather than a timeout. Options set per call do
     * reach the wire, and merge over the configured model and token limit rather than
     * replacing them.
     *
     * <p>Provider-specific: pointing {@code spring.ai.model.chat} at anthropic means changing
     * this line too.
     */
    private OpenAiChatOptions.Builder timeoutOption() {
        return OpenAiChatOptions.builder().timeout(properties.timeout());
    }

    private static int wordCount(String text) {
        return text.trim().split("\\s+").length;
    }

    /**
     * The article as the model will see it. Guards the context window on very long articles:
     * truncating and telling the user nothing beats failing the bookmark outright, but it is
     * lossy, so it is logged. The listener brief reads the same input the narration does.
     */
    String modelInput(String article) {
        String[] words = article.trim().split("\\s+");
        if (words.length <= properties.maxInputWords()) {
            return article;
        }
        log.warn("Truncating article from {} to {} words before summarising",
                words.length, properties.maxInputWords());
        return String.join(" ", java.util.Arrays.copyOf(words, properties.maxInputWords()));
    }
}
