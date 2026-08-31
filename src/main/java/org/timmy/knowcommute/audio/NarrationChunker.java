package org.timmy.knowcommute.audio;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Splits a narration script into pieces small enough to synthesise in one request.
 *
 * <p>Cuts land on paragraph breaks first and sentence breaks second, because a speech model
 * already renders a falling contour and a gap at a full stop — a seam placed there is a pause
 * the listener expects anyway. A cut mid-sentence is audible in a way no amount of careful
 * joining fixes: the model closes the first fragment as a finished thought and opens the second
 * as a new one.
 *
 * <p>Pieces are packed up toward the limit rather than split as small as they could be. Each
 * request is synthesised without the surrounding text, and a model handed one short sentence
 * stresses it as a standalone utterance, so over-small chunks sound clipped.
 */
final class NarrationChunker {

    private static final Pattern PARAGRAPH_BREAK = Pattern.compile("\\R\\s*\\R");
    private static final Pattern SENTENCE_BREAK = Pattern.compile("(?<=[.!?])\\s+");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private static final String PARAGRAPH_SEPARATOR = "\n\n";
    private static final String SENTENCE_SEPARATOR = " ";

    private NarrationChunker() {
    }

    static List<String> chunk(String narration, int maxChars) {
        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        for (String paragraph : PARAGRAPH_BREAK.split(narration.trim())) {
            String trimmed = paragraph.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (trimmed.length() <= maxChars) {
                pack(chunks, current, trimmed, PARAGRAPH_SEPARATOR, maxChars);
                continue;
            }
            // An over-long paragraph starts its own chunk, so every seam inside it is a
            // sentence break rather than the paragraph break that preceded it.
            flush(chunks, current);
            for (String sentence : sentencesWithin(trimmed, maxChars)) {
                pack(chunks, current, sentence, SENTENCE_SEPARATOR, maxChars);
            }
            flush(chunks, current);
        }

        flush(chunks, current);
        return List.copyOf(chunks);
    }

    private static void pack(List<String> chunks, StringBuilder current, String piece,
                             String separator, int maxChars) {
        if (!current.isEmpty() && current.length() + separator.length() + piece.length() > maxChars) {
            flush(chunks, current);
        }
        if (!current.isEmpty()) {
            current.append(separator);
        }
        current.append(piece);
    }

    private static void flush(List<String> chunks, StringBuilder current) {
        if (!current.isEmpty()) {
            chunks.add(current.toString());
            current.setLength(0);
        }
    }

    private static List<String> sentencesWithin(String paragraph, int maxChars) {
        List<String> sentences = new ArrayList<>();
        for (String sentence : SENTENCE_BREAK.split(paragraph)) {
            if (sentence.length() <= maxChars) {
                sentences.add(sentence);
            } else {
                sentences.addAll(splitOnWords(sentence, maxChars));
            }
        }
        return sentences;
    }

    /**
     * A sentence longer than a whole chunk offers nowhere good to cut, so the seam goes on a
     * word boundary and is audible. Reachable only on text with no sentence punctuation, where
     * the alternative is a request too large to finish.
     */
    private static List<String> splitOnWords(String sentence, int maxChars) {
        List<String> runs = new ArrayList<>();
        StringBuilder run = new StringBuilder();
        for (String word : WHITESPACE.split(sentence)) {
            if (!run.isEmpty() && run.length() + 1 + word.length() > maxChars) {
                runs.add(run.toString());
                run.setLength(0);
            }
            if (!run.isEmpty()) {
                run.append(' ');
            }
            run.append(word);
        }
        if (!run.isEmpty()) {
            runs.add(run.toString());
        }
        return runs;
    }
}
