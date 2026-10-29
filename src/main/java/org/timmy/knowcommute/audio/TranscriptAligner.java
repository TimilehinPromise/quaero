package org.timmy.knowcommute.audio;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class TranscriptAligner {

    private static final Pattern TOKEN = Pattern.compile("\\S+");
    private static final double SOURCE_SKIP_COST = 6;
    private static final double EXTRA_CAPTION_COST = 0.25;
    private static final double MISMATCH_COST = 5;

    private TranscriptAligner() {
    }

    static List<WordTiming> align(String summary, NarrationChunk chunk, List<SpeechCaption> captions,
                                  double chunkOffset) {
        List<SourceWord> source = sourceWords(summary, chunk);
        if (source.isEmpty()) {
            if (captions.isEmpty()) {
                return List.of();
            }
            throw new AudioGenerationException("Kokoro returned captions for text with no words");
        }
        if (captions.isEmpty()) {
            throw new AudioGenerationException("Kokoro returned no word timestamps for a narration part");
        }

        List<List<SpeechCaption>> assigned = align(source, captions);
        List<WordTiming> timings = new ArrayList<>(source.size());
        for (int index = 0; index < source.size(); index++) {
            List<SpeechCaption> words = assigned.get(index);
            if (words.isEmpty()) {
                words = nearestAssigned(assigned, index);
            }
            SourceWord word = source.get(index);
            double start = words.stream().mapToDouble(SpeechCaption::start).min().orElseThrow() + chunkOffset;
            double end = words.stream().mapToDouble(SpeechCaption::end).max().orElseThrow() + chunkOffset;
            timings.add(new WordTiming(start, end, word.start(), word.end()));
        }
        return List.copyOf(timings);
    }

    private static List<List<SpeechCaption>> align(List<SourceWord> source, List<SpeechCaption> captions) {
        List<List<SpeechCaption>> assigned = emptyAssignments(source.size());
        List<Anchor> anchors = exactAnchors(source, captions);
        int sourceStart = 0;
        int captionStart = 0;
        for (Anchor anchor : anchors) {
            alignSegment(source, captions, assigned, sourceStart, anchor.sourceIndex(),
                    captionStart, anchor.captionIndex());
            assigned.get(anchor.sourceIndex()).add(captions.get(anchor.captionIndex()));
            sourceStart = anchor.sourceIndex() + 1;
            captionStart = anchor.captionIndex() + 1;
        }
        alignSegment(source, captions, assigned, sourceStart, source.size(), captionStart, captions.size());
        return assigned;
    }

    private static List<Anchor> exactAnchors(List<SourceWord> source, List<SpeechCaption> captions) {
        int[][] lengths = new int[source.size() + 1][captions.size() + 1];
        for (int sourceIndex = source.size() - 1; sourceIndex >= 0; sourceIndex--) {
            for (int captionIndex = captions.size() - 1; captionIndex >= 0; captionIndex--) {
                lengths[sourceIndex][captionIndex] = exactMatch(source.get(sourceIndex), captions.get(captionIndex))
                        ? lengths[sourceIndex + 1][captionIndex + 1] + 1
                        : Math.max(lengths[sourceIndex + 1][captionIndex], lengths[sourceIndex][captionIndex + 1]);
            }
        }

        List<Anchor> anchors = new ArrayList<>();
        int sourceIndex = 0;
        int captionIndex = 0;
        while (sourceIndex < source.size() && captionIndex < captions.size()) {
            if (exactMatch(source.get(sourceIndex), captions.get(captionIndex))
                    && lengths[sourceIndex][captionIndex] == lengths[sourceIndex + 1][captionIndex + 1] + 1) {
                anchors.add(new Anchor(sourceIndex, captionIndex));
                sourceIndex++;
                captionIndex++;
            } else if (lengths[sourceIndex + 1][captionIndex] >= lengths[sourceIndex][captionIndex + 1]) {
                sourceIndex++;
            } else {
                captionIndex++;
            }
        }
        return anchors;
    }

    private static boolean exactMatch(SourceWord source, SpeechCaption caption) {
        String sourceText = canonical(source.text());
        return !sourceText.isEmpty() && sourceText.equals(canonical(caption.word()));
    }

    private static void alignSegment(List<SourceWord> source, List<SpeechCaption> captions,
                                     List<List<SpeechCaption>> assigned, int sourceStart, int sourceEnd,
                                     int captionStart, int captionEnd) {
        int sourceCount = sourceEnd - sourceStart;
        int captionCount = captionEnd - captionStart;
        if (captionCount == 0) {
            return;
        }
        if (sourceCount == 0) {
            int target = sourceStart > 0 ? sourceStart - 1 : sourceStart;
            if (sourceStart > 0) {
                assigned.get(target).addAll(captions.subList(captionStart, captionEnd));
            } else {
                assigned.get(target).addAll(0, captions.subList(captionStart, captionEnd));
            }
            return;
        }
        if (sourceCount == 1) {
            assigned.get(sourceStart).addAll(captions.subList(captionStart, captionEnd));
            return;
        }
        if (captionCount == 1) {
            for (int sourceIndex = sourceStart; sourceIndex < sourceEnd; sourceIndex++) {
                assigned.get(sourceIndex).add(captions.get(captionStart));
            }
            return;
        }

        List<List<SpeechCaption>> segment = alignUnmatched(source.subList(sourceStart, sourceEnd),
                captions.subList(captionStart, captionEnd));
        for (int index = 0; index < sourceCount; index++) {
            assigned.get(sourceStart + index).addAll(segment.get(index));
        }
    }

    private static List<List<SpeechCaption>> alignUnmatched(List<SourceWord> source, List<SpeechCaption> captions) {
        double[][] costs = new double[source.size() + 1][captions.size() + 1];
        Operation[][] operations = new Operation[source.size() + 1][captions.size() + 1];
        for (double[] row : costs) {
            Arrays.fill(row, Double.POSITIVE_INFINITY);
        }
        costs[0][0] = 0;

        for (int sourceIndex = 0; sourceIndex <= source.size(); sourceIndex++) {
            for (int captionIndex = 0; captionIndex <= captions.size(); captionIndex++) {
                double cost = costs[sourceIndex][captionIndex];
                if (!Double.isFinite(cost)) {
                    continue;
                }
                if (sourceIndex < source.size() && captionIndex < captions.size()) {
                    double pairCost = cost + pairCost(source.get(sourceIndex), captions.get(captionIndex));
                    update(costs, operations, sourceIndex + 1, captionIndex + 1, pairCost, Operation.PAIR);
                }
                if (sourceIndex < source.size()) {
                    update(costs, operations, sourceIndex + 1, captionIndex,
                            cost + SOURCE_SKIP_COST, Operation.SOURCE_SKIP);
                }
                if (sourceIndex > 0 && captionIndex < captions.size()) {
                    update(costs, operations, sourceIndex, captionIndex + 1,
                            cost + EXTRA_CAPTION_COST, Operation.EXTRA_CAPTION);
                }
            }
        }

        List<List<SpeechCaption>> assigned = emptyAssignments(source.size());
        int sourceIndex = source.size();
        int captionIndex = captions.size();
        while (sourceIndex > 0 || captionIndex > 0) {
            Operation operation = operations[sourceIndex][captionIndex];
            if (operation == null) {
                throw new AudioGenerationException("Could not map Kokoro captions to the transcript");
            }
            switch (operation) {
                case PAIR -> {
                    assigned.get(sourceIndex - 1).addFirst(captions.get(captionIndex - 1));
                    sourceIndex--;
                    captionIndex--;
                }
                case EXTRA_CAPTION -> {
                    assigned.get(sourceIndex - 1).addFirst(captions.get(captionIndex - 1));
                    captionIndex--;
                }
                case SOURCE_SKIP -> sourceIndex--;
            }
        }
        return assigned;
    }

    private static List<List<SpeechCaption>> emptyAssignments(int size) {
        List<List<SpeechCaption>> assigned = new ArrayList<>(size);
        for (int index = 0; index < size; index++) {
            assigned.add(new ArrayList<>());
        }
        return assigned;
    }

    private record Anchor(int sourceIndex, int captionIndex) {
    }

    private static void update(double[][] costs, Operation[][] operations, int sourceIndex, int captionIndex,
                               double cost, Operation operation) {
        if (cost < costs[sourceIndex][captionIndex]) {
            costs[sourceIndex][captionIndex] = cost;
            operations[sourceIndex][captionIndex] = operation;
        }
    }

    private static double pairCost(SourceWord source, SpeechCaption caption) {
        String sourceText = canonical(source.text());
        String captionText = canonical(caption.word());
        if (sourceText.equals(captionText)) {
            return 0;
        }
        if (!sourceText.isEmpty() && !captionText.isEmpty()
                && (sourceText.startsWith(captionText) || captionText.startsWith(sourceText))) {
            return 0.5;
        }
        return MISMATCH_COST;
    }

    private static String canonical(String text) {
        String normalised = Normalizer.normalize(text, Normalizer.Form.NFKD).toLowerCase(Locale.ROOT);
        StringBuilder result = new StringBuilder(normalised.length());
        normalised.codePoints()
                .filter(codePoint -> Character.isLetterOrDigit(codePoint))
                .forEach(result::appendCodePoint);
        return result.toString();
    }

    private static List<SpeechCaption> nearestAssigned(List<List<SpeechCaption>> assigned, int sourceIndex) {
        for (int distance = 1; distance < assigned.size(); distance++) {
            if (sourceIndex - distance >= 0 && !assigned.get(sourceIndex - distance).isEmpty()) {
                return assigned.get(sourceIndex - distance);
            }
            if (sourceIndex + distance < assigned.size() && !assigned.get(sourceIndex + distance).isEmpty()) {
                return assigned.get(sourceIndex + distance);
            }
        }
        throw new AudioGenerationException("Kokoro returned no usable word timestamps");
    }

    private static List<SourceWord> sourceWords(String summary, NarrationChunk chunk) {
        List<SourceWord> words = new ArrayList<>();
        Matcher matcher = TOKEN.matcher(chunk.text());
        int searchFrom = chunk.transcriptStart();
        while (matcher.find()) {
            String text = matcher.group();
            int start = summary.indexOf(text, searchFrom);
            if (start < 0 || start >= chunk.transcriptEnd()) {
                throw new AudioGenerationException("Could not locate a narration chunk in the transcript");
            }
            int end = start + text.length();
            words.add(new SourceWord(text, start, end));
            searchFrom = end;
        }
        return words;
    }

    private enum Operation {
        PAIR,
        EXTRA_CAPTION,
        SOURCE_SKIP
    }

    private record SourceWord(String text, int start, int end) {
    }
}
