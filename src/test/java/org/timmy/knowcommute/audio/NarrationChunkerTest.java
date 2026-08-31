package org.timmy.knowcommute.audio;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class NarrationChunkerTest {

    private static final String SENTENCE = "Deterministic execution cannot produce an error. ";

    private static String repeat(String text, int times) {
        return text.repeat(times).trim();
    }

    @Test
    void keepsAShortNarrationWhole() {
        assertThat(NarrationChunker.chunk("One sentence. And a second.", 1200))
                .containsExactly("One sentence. And a second.");
    }

    @Test
    void keepsEveryChunkWithinTheLimit() {
        List<String> chunks = NarrationChunker.chunk(repeat(SENTENCE, 60), 400);

        assertThat(chunks).isNotEmpty().allSatisfy(chunk -> assertThat(chunk).hasSizeLessThanOrEqualTo(400));
    }

    @Test
    void cutsOnSentenceBoundariesSoSeamsLandWhereAPauseBelongs() {
        List<String> chunks = NarrationChunker.chunk(repeat(SENTENCE, 20), 300);

        assertThat(chunks).hasSizeGreaterThan(1)
                .allSatisfy(chunk -> assertThat(chunk).endsWith("."));
    }

    @Test
    void preservesEveryWordAcrossTheSplit() {
        String narration = repeat(SENTENCE, 40);

        String rejoined = String.join(" ", NarrationChunker.chunk(narration, 350))
                .replaceAll("\\s+", " ");

        assertThat(rejoined).isEqualTo(narration.replaceAll("\\s+", " "));
    }

    @Test
    void packsWholeParagraphsTogetherRatherThanEmittingOnePerChunk() {
        String narration = "First paragraph here.\n\nSecond paragraph here.\n\nThird paragraph here.";

        assertThat(NarrationChunker.chunk(narration, 1200)).containsExactly(
                "First paragraph here.\n\nSecond paragraph here.\n\nThird paragraph here.");
    }

    @Test
    void keepsTheParagraphBreakWhenParagraphsShareAChunk() {
        List<String> chunks = NarrationChunker.chunk("Alpha beta.\n\nGamma delta.", 1200);

        assertThat(chunks).singleElement().asString().contains("\n\n");
    }

    @Test
    void startsAFreshChunkForAParagraphTooLongToFit() {
        String narration = "Short intro.\n\n" + repeat(SENTENCE, 20);

        List<String> chunks = NarrationChunker.chunk(narration, 300);

        assertThat(chunks.getFirst()).isEqualTo("Short intro.");
    }

    @Test
    void fallsBackToWordBoundariesForTextWithNoSentencePunctuation() {
        List<String> chunks = NarrationChunker.chunk("word ".repeat(200).trim(), 100);

        assertThat(chunks).allSatisfy(chunk -> assertThat(chunk).hasSizeLessThanOrEqualTo(100));
        assertThat(chunks.stream().mapToInt(chunk -> chunk.split(" ").length).sum()).isEqualTo(200);
    }

    @Test
    void ignoresBlankParagraphs() {
        assertThat(NarrationChunker.chunk("Alpha.\n\n\n\n   \n\nBeta.", 1200))
                .containsExactly("Alpha.\n\nBeta.");
    }
}
