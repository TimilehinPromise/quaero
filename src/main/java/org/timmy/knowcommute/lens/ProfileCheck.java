package org.timmy.knowcommute.lens;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Locale;
import java.util.Map;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

/**
 * Screens a listener profile before it is saved. The profile is later inserted into the brief
 * prompt, which already treats it as data; this is the second guard, and the one that can tell
 * the listener why their text was refused.
 */
@Component
public class ProfileCheck {

    /**
     * Passed as a parameter rather than written into the template: the template renderer uses
     * braces as its own delimiters, so a literal JSON example in the file would not render.
     */
    private static final String SHAPE =
            "{\"verdict\": \"ok\" | \"instructions\" | \"abusive\" | \"not_a_description\", \"reason\": \"...\"}";

    private static final ObjectMapper JSON = new ObjectMapper();

    private final ChatClient.Builder chatClientBuilder;
    private final LensProperties properties;
    private final Resource template;

    public ProfileCheck(ChatClient.Builder chatClientBuilder, LensProperties properties,
                        @Value("classpath:prompts/check-listener-profile.st") Resource template) {
        this.chatClientBuilder = chatClientBuilder;
        this.properties = properties;
        this.template = template;
    }

    public Verdict check(String profile) {
        String reply;
        try {
            reply = chatClientBuilder.build().prompt()
                    .options(OpenAiChatOptions.builder().timeout(properties.checkTimeout()))
                    .user(spec -> spec.text(template).params(Map.of("profile", profile, "shape", SHAPE)))
                    .call()
                    .content();
        } catch (RuntimeException e) {
            throw new ProfileCheckUnavailableException(e);
        }
        return parse(reply);
    }

    static Verdict parse(String reply) {
        if (reply == null || !reply.contains("{") || !reply.contains("}")) {
            throw new ProfileCheckUnavailableException("The check returned no verdict");
        }
        try {
            JsonNode node = JSON.readTree(
                    reply.substring(reply.indexOf('{'), reply.lastIndexOf('}') + 1));
            Outcome outcome = Outcome.valueOf(node.path("verdict").asText().toUpperCase(Locale.ROOT));
            return new Verdict(outcome, node.path("reason").asText(""));
        } catch (Exception e) {
            throw new ProfileCheckUnavailableException("The check returned an unreadable verdict");
        }
    }

    public enum Outcome { OK, INSTRUCTIONS, ABUSIVE, NOT_A_DESCRIPTION }

    public record Verdict(Outcome outcome, String reason) {

        public boolean accepted() {
            return outcome == Outcome.OK;
        }
    }
}
