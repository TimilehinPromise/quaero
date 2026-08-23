package org.timmy.knowcommute.summary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.util.List;
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
    private final AtomicReference<String> reply = new AtomicReference<>("a spoken summary");

    private final ChatModel stubModel = prompt -> {
        lastPrompt.set(prompt.getContents());
        return new ChatResponse(List.of(new Generation(new AssistantMessage(reply.get()))));
    };

    private final Resource template = new ByteArrayResource(TEMPLATE.getBytes()) {
        @Override
        public String getFilename() {
            return "summarise-article.st";
        }
    };

    private SummarizationService service(int maxInputWords) {
        return new SummarizationService(ChatClient.builder(stubModel),
                new SummarizationProperties(maxInputWords), template);
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
    void rejectsAnEmptyModelResponse() {
        reply.set("   ");

        assertThatExceptionOfType(SummarizationException.class).isThrownBy(() ->
                service(40_000).summarise("t", "example.com", "body", SummaryLength.MEDIUM));
    }

    @Test
    void handlesMissingTitleAndDomain() {
        String summary = service(40_000)
                .summarise(null, null, "the article body", SummaryLength.MEDIUM);

        assertThat(summary).isEqualTo("a spoken summary");
        assertThat(lastPrompt.get()).contains("Untitled").contains("unknown");
    }
}
