package org.timmy.knowcommute.figure;

import java.util.Map;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.timmy.knowcommute.bookmark.Bookmark;
import org.timmy.knowcommute.extraction.FetchedPage;

@Service
public class FigureVisionService {

    private final ChatClient.Builder chatClientBuilder;
    private final FigureProperties properties;
    private final Resource promptTemplate;
    private final FigureDebugService debug;

    @Autowired
    public FigureVisionService(ChatClient.Builder chatClientBuilder, FigureProperties properties,
                               @Value("classpath:prompts/extract-figure-knowledge.st") Resource promptTemplate,
                               FigureDebugService debug) {
        this.chatClientBuilder = chatClientBuilder;
        this.properties = properties;
        this.promptTemplate = promptTemplate;
        this.debug = debug;
    }

    FigureVisionService(ChatClient.Builder chatClientBuilder, FigureProperties properties, Resource promptTemplate) {
        this(chatClientBuilder, properties, promptTemplate,
                new FigureDebugService(new FigureDebugProperties("")));
    }

    public VisionResult extract(BookmarkFigure figure, String title, FetchedPage image) {
        return extract(null, figure, title, image);
    }

    public VisionResult extract(Bookmark bookmark, BookmarkFigure figure, FetchedPage image) {
        return extract(bookmark, figure, bookmark == null ? null : bookmark.getTitle(), image);
    }

    private VisionResult extract(Bookmark bookmark, BookmarkFigure figure, String title, FetchedPage image) {
        String prompt = new PromptTemplate(promptTemplate).render(Map.of(
                "title", value(title),
                "caption", value(figure.getCaption()),
                "altText", value(figure.getAltText()),
                "precedingParagraph", value(figure.getPrecedingParagraph()),
                "followingParagraph", value(figure.getFollowingParagraph())));
        debug.request(bookmark, figure, image, prompt);
        try {
            ChatResponse response = chatClientBuilder.build().prompt()
                    .options(OpenAiChatOptions.builder()
                            .model(properties.model())
                            .timeout(properties.timeout())
                            .maxTokens(properties.maxOutputTokens()))
                    .user(spec -> spec.text(prompt)
                            .media(image.contentType(), new ByteArrayResource(image.body())))
                    .call()
                    .chatResponse();

            String text = textOf(response);
            debug.response(figure, text);
            if (hitTokenLimit(response)) {
                throw new FigureVisionException("Vision model hit its output limit");
            }
            if (text == null || text.isBlank()) {
                throw new FigureVisionException("Vision model returned an empty response");
            }
            String knowledge = text.trim();
            Usage usage = response.getMetadata() == null ? null : response.getMetadata().getUsage();
            return new VisionResult("NONE".equalsIgnoreCase(knowledge) ? null : knowledge,
                    usage == null ? null : usage.getPromptTokens(),
                    usage == null ? null : usage.getCompletionTokens(),
                    usage == null ? null : usage.getTotalTokens());
        } catch (RuntimeException failure) {
            debug.error(figure, failure);
            throw failure;
        }
    }

    private static boolean hitTokenLimit(ChatResponse response) {
        String finishReason = response == null || response.getResult() == null
                ? null : response.getResult().getMetadata().getFinishReason();
        return "LENGTH".equalsIgnoreCase(finishReason) || "MAX_TOKENS".equalsIgnoreCase(finishReason);
    }

    private static String textOf(ChatResponse response) {
        return response == null || response.getResult() == null
                ? null : response.getResult().getOutput().getText();
    }

    private static String value(String value) {
        return value == null || value.isBlank() ? "none" : value;
    }
}
