package org.timmy.knowcommute.summary;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

/**
 * Decides, before the narration is written, what one listener needs explained and whether the
 * article bears on their work. It is a separate call because one prompt asked to compress, keep
 * coverage, explain, and judge relevance at once kept trading one of those for another; and the
 * profile stops here — the narration only ever sees the notes.
 */
@Slf4j
@Service
public class ListenerBriefService {

    /** A parameter, not template text: the renderer treats braces as its own delimiters. */
    private static final String SHAPE =
            "{\"terms\": [{\"term\": \"...\", \"explain\": \"...\", \"analogy\": \"...\" or null}], "
                    + "\"takeaway\": \"...\" or null}";

    /**
     * The prompt asks for at most two, and a model has ignored softer wording and given every
     * term one. Terms arrive most important first, so the first two comparisons are the ones kept.
     */
    private static final int MAX_COMPARISONS = 2;

    private static final ObjectMapper JSON = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private final ChatClient.Builder chatClientBuilder;
    private final SummarizationService summarizationService;
    private final SummarizationProperties properties;
    private final Resource briefTemplate;
    private final String notesTemplate;

    public ListenerBriefService(ChatClient.Builder chatClientBuilder, SummarizationService summarizationService,
                                SummarizationProperties properties,
                                @Value("classpath:prompts/listener-brief.st") Resource briefTemplate,
                                @Value("classpath:prompts/listener-notes.txt") Resource notesTemplate) {
        this.chatClientBuilder = chatClientBuilder;
        this.summarizationService = summarizationService;
        this.properties = properties;
        this.briefTemplate = briefTemplate;
        this.notesTemplate = read(notesTemplate);
    }

    /** One retry on an unreadable reply; a model that wraps or breaks its JSON usually won't twice. */
    public ListenerBrief brief(String listener, String title, String sourceDomain, String article) {
        String input = summarizationService.modelInput(article);
        try {
            return parse(ask(listener, title, sourceDomain, input));
        } catch (SummarizationException first) {
            log.warn("Listener brief was unreadable ({}); asking once more", first.getMessage());
            return parse(ask(listener, title, sourceDomain, input));
        }
    }

    public ListenerNotes notesFor(ListenerBrief brief) {
        String takeaway = brief.takeaway() == null ? "" : brief.takeaway();
        if (brief.terms().isEmpty()) {
            return new ListenerNotes("", takeaway);
        }
        int[] comparisons = {0};
        String notes = brief.terms().stream()
                .map(term -> {
                    boolean compare = term.analogy() != null && !term.analogy().isBlank()
                            && comparisons[0]++ < MAX_COMPARISONS;
                    return "- " + term.term() + ": " + term.explain() + (compare ? " Comparison: " + term.analogy() : "");
                })
                .collect(Collectors.joining("\n"));
        return new ListenerNotes(notesTemplate.replace("{notes}", notes) + "\n", takeaway);
    }

    public static String toJson(ListenerBrief brief) {
        try {
            return JSON.writeValueAsString(brief);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("A listener brief always serialises", e);
        }
    }

    private String ask(String listener, String title, String sourceDomain, String input) {
        return chatClientBuilder.build().prompt()
                .options(OpenAiChatOptions.builder().timeout(properties.timeout()))
                .user(spec -> spec.text(briefTemplate).params(Map.of(
                        "listener", listener,
                        "shape", SHAPE,
                        "title", title == null ? "Untitled" : title,
                        "sourceDomain", sourceDomain == null ? "unknown" : sourceDomain,
                        "article", input)))
                .call()
                .content();
    }

    static ListenerBrief parse(String reply) {
        if (reply == null || reply.indexOf('{') < 0 || reply.lastIndexOf('}') < reply.indexOf('{')) {
            throw new SummarizationException("Listener brief contained no JSON");
        }
        try {
            return JSON.readValue(reply.substring(reply.indexOf('{'), reply.lastIndexOf('}') + 1),
                    ListenerBrief.class);
        } catch (JsonProcessingException e) {
            throw new SummarizationException("Listener brief JSON was malformed");
        }
    }

    private static String read(Resource resource) {
        try {
            return resource.getContentAsString(StandardCharsets.UTF_8).strip();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
