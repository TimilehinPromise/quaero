package org.timmy.knowcommute.summary;

/**
 * What a listener brief contributes to one narration: a block for the prompt, and a takeaway
 * appended by code after the narration is written, so it is never cut by the tightening pass.
 */
public record ListenerNotes(String promptBlock, String takeaway) {

    public static final ListenerNotes NONE = new ListenerNotes("", "");

    int takeawayWords() {
        return takeaway.isBlank() ? 0 : takeaway.trim().split("\\s+").length;
    }

    String appendTo(String narration) {
        return takeaway.isBlank() ? narration : narration + "\n\n" + takeaway.trim();
    }
}
