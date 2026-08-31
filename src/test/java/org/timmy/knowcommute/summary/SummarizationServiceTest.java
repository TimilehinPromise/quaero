package org.timmy.knowcommute.summary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.timmy.knowcommute.bookmark.SummaryLength;

class SummarizationServiceTest {

    private static final String TEMPLATE = """
            Rewrite in roughly {targetWords} words.
            Title: {title}
            Source: {sourceDomain}
            Article:
            {article}
            """;

    private final AtomicReference<String> lastPrompt = new AtomicReference<>();
    private final AtomicInteger modelCalls = new AtomicInteger();
    /** Replies are consumed in order; once drained the stub keeps answering with the default. */
    private final Queue<String> replies = new ConcurrentLinkedQueue<>();

    private final ChatModel stubModel = prompt -> {
        modelCalls.incrementAndGet();
        lastPrompt.set(prompt.getContents());
        String next = replies.poll();
        return new ChatResponse(List.of(new Generation(new AssistantMessage(
                next == null ? "a spoken summary" : next))));
    };

    private final Resource template = new ByteArrayResource(TEMPLATE.getBytes()) {
        @Override
        public String getFilename() {
            return "summarise-article.st";
        }
    };

    /** A fraction this large never binds, so these cases exercise the ceiling machinery alone. */
    private static final double NO_CLAMP = 100_000.0;

    private SummarizationService service(int maxInputWords) {
        return service(maxInputWords, NO_CLAMP, 1);
    }

    private SummarizationService service(int maxInputWords, double maxSourceFraction, int minWords) {
        return new SummarizationService(ChatClient.builder(stubModel),
                new SummarizationProperties(maxInputWords, java.time.Duration.ofMinutes(5),
                        maxSourceFraction, minWords),
                template);
    }

    @Test
    void returnsTheModelsSummary() {
        String summary = service(40_000)
                .summarise("A Title", "example.com", "the article body", SummaryLength.MEDIUM);

        assertThat(summary).isEqualTo("a spoken summary");
    }

    @Test
    void putsTheTargetLengthAndArticleIntoThePrompt() {
        service(40_000).summarise("A Title", "example.com", "the article body", SummaryLength.SHORT);

        assertThat(lastPrompt.get())
                .contains(String.valueOf(SummaryLength.SHORT.targetWords()))
                .contains("A Title")
                .contains("example.com")
                .contains("the article body");
    }

    @Test
    void eachLengthAsksForADifferentWordCount() {
        service(40_000).summarise("t", "example.com", "body", SummaryLength.SHORT);
        String shortPrompt = lastPrompt.get();
        service(40_000).summarise("t", "example.com", "body", SummaryLength.LONG);

        assertThat(shortPrompt).isNotEqualTo(lastPrompt.get());
        assertThat(lastPrompt.get()).contains(String.valueOf(SummaryLength.LONG.targetWords()));
    }

    /** "zyzzyva" cannot collide with the template's own wording, unlike "word" in "words". */
    private long occurrences(String token) {
        return java.util.Arrays.stream(lastPrompt.get().split("\\s+"))
                .filter(token::equals).count();
    }

    @Test
    void truncatesOverlongArticlesRatherThanFailingTheBookmark() {
        String longArticle = "zyzzyva ".repeat(500);

        String summary = service(100)
                .summarise("A Title", "example.com", longArticle, SummaryLength.MEDIUM);

        assertThat(summary).isEqualTo("a spoken summary");
        assertThat(occurrences("zyzzyva")).isEqualTo(100);
    }

    @Test
    void doesNotTruncateArticlesInsideTheBudget() {
        service(1000).summarise("t", "example.com", "zyzzyva ".repeat(200), SummaryLength.MEDIUM);

        assertThat(occurrences("zyzzyva")).isEqualTo(200);
    }

    @Test
    void asksForFewerWordsThanRequestedWhenTheArticleCannotSupportThem() {
        service(40_000, 0.33, 150)
                .summarise("t", "example.com", "word ".repeat(2_036), SummaryLength.LONG);

        assertThat(lastPrompt.get())
                .contains("672")
                .doesNotContain(String.valueOf(SummaryLength.LONG.targetWords()));
    }

    @Test
    void stillAsksForTheFullLengthWhenTheArticleIsLongEnough() {
        service(40_000, 0.33, 150)
                .summarise("t", "example.com", "word ".repeat(6_000), SummaryLength.LONG);

        assertThat(lastPrompt.get()).contains(String.valueOf(SummaryLength.LONG.targetWords()));
    }

    @Test
    void judgesACachedSummaryAgainstTheArticleItWouldBeServedFor() {
        String cached = "word ".repeat(1_400).trim();
        SummarizationService service = service(40_000, 0.33, 150);

        assertThat(service.exceedsCeiling(cached, SummaryLength.LONG, "word ".repeat(6_000)))
                .isFalse();
        assertThat(service.exceedsCeiling(cached, SummaryLength.LONG, "word ".repeat(2_036)))
                .isTrue();
    }

    @Test
    void rejectsAnEmptyModelResponse() {
        replies.add("   ");

        assertThatExceptionOfType(SummarizationException.class).isThrownBy(() ->
                service(40_000).summarise("t", "example.com", "body", SummaryLength.MEDIUM));
    }

    @Test
    void asksTheModelToTightenWhenTheFirstDraftBreachesTheCeiling() {
        replies.add("word ".repeat(1_000));
        replies.add("tightened summary");

        String summary = service(40_000)
                .summarise("t", "example.com", "the article body", SummaryLength.MEDIUM);

        assertThat(summary).isEqualTo("tightened summary");
        assertThat(modelCalls).hasValue(2);
        assertThat(lastPrompt.get())
                .contains("1000 words")
                .contains(String.valueOf(SummaryLength.MEDIUM.targetWords()));
    }

    @Test
    void truncatesAtASentenceBoundaryWhenTheModelIgnoresTheCorrection() {
        replies.add("Short sentence here. ".repeat(400));
        replies.add("Still too long. ".repeat(400));

        String summary = service(40_000)
                .summarise("t", "example.com", "the article body", SummaryLength.MEDIUM);

        assertThat(summary.trim().split("\\s+")).hasSizeLessThanOrEqualTo(SummaryLength.MEDIUM.targetWords());
        assertThat(summary).endsWith(".");
        assertThat(modelCalls).hasValue(2);
    }

    @Test
    void acceptsOvershootWithinTheToleratedCeilingWithoutRetrying() {
        // Over the 750 target but under the 1.30x gate: no retry, no truncation.
        replies.add("word ".repeat(900));

        String summary = service(40_000)
                .summarise("t", "example.com", "the article body", SummaryLength.MEDIUM);

        assertThat(summary.trim().split("\\s+")).hasSize(900);
        assertThat(modelCalls).hasValue(1);
    }

    @Test
    void handlesMissingTitleAndDomain() {
        String summary = service(40_000)
                .summarise(null, null, "the article body", SummaryLength.MEDIUM);

        assertThat(summary).isEqualTo("a spoken summary");
        assertThat(lastPrompt.get()).contains("Untitled").contains("unknown");
    }
}
