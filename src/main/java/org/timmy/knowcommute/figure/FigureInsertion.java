package org.timmy.knowcommute.figure;

public record FigureInsertion(
        int figureOrdinal,
        String insertedText,
        int startOffset,
        int endOffset,
        String contextBefore,
        String contextAfter) {

    public static FigureInsertion inserted(int ordinal, String text, int start, int end,
            String before, String after) {
        return new FigureInsertion(ordinal, text, start, end, before, after);
    }

    public static FigureInsertion notInserted(int ordinal) {
        return new FigureInsertion(ordinal, null, -1, -1, null, null);
    }
}
