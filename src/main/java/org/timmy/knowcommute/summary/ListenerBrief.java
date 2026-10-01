package org.timmy.knowcommute.summary;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * What one listener needs from one article: the terms to explain, and a takeaway only when the
 * article bears on their work. Stored on the bookmark as JSON, so a surprising narration can be
 * traced back to the decisions that shaped it.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ListenerBrief(List<Term> terms, String takeaway) {

    /**
     * Where a description of the particular listener starts inside "For a …,": the kind of work
     * comes first, and a qualifier after it — "building payment services", "on a consumer mobile
     * app" — is their profile read back to them.
     */
    private static final Pattern QUALIFIER = Pattern.compile(
            "\\s(?:who|that|whose|building|running|working|leading|on|at|in|with|from)\\s",
            Pattern.CASE_INSENSITIVE);

    /** Longer than this before the first comma, and the comma is not closing the opening phrase. */
    private static final int OPENING_WORDS = 12;

    public ListenerBrief {
        terms = terms == null ? List.of() : List.copyOf(terms);
        takeaway = takeaway == null || takeaway.isBlank() ? null : kindOfWorkOnly(takeaway.strip());
    }

    /**
     * The prompt asks for the kind of work only and a model has still echoed the profile, so the
     * opening is cut back in code: "For a backend engineer building payment services, this…"
     * becomes "For a backend engineer, this…". Only the text before the first comma is touched.
     */
    static String kindOfWorkOnly(String takeaway) {
        int comma = takeaway.indexOf(',');
        if (!takeaway.startsWith("For a") || comma < 0
                || takeaway.substring(0, comma).split("\\s+").length > OPENING_WORDS) {
            return takeaway;
        }
        Matcher qualifier = QUALIFIER.matcher(takeaway.substring(0, comma));
        return qualifier.find() ? takeaway.substring(0, qualifier.start()) + takeaway.substring(comma) : takeaway;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Term(String term, String explain, String analogy) {
    }
}
