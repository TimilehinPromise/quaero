package org.timmy.knowcommute.summary;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * What one listener needs from one article: the terms to explain, and a takeaway only when the
 * article bears on their work. Stored on the bookmark as JSON, so a surprising narration can be
 * traced back to the decisions that shaped it.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ListenerBrief(List<Term> terms, String takeaway) {

    public ListenerBrief {
        terms = terms == null ? List.of() : List.copyOf(terms);
        takeaway = takeaway == null || takeaway.isBlank() ? null : takeaway.strip();
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Term(String term, String explain, String analogy) {
    }
}
