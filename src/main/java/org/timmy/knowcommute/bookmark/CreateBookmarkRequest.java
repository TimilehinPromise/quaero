package org.timmy.knowcommute.bookmark;

import jakarta.validation.constraints.NotBlank;

/** {@code useLens} left out means "use my listener profile if I have one". */
public record CreateBookmarkRequest(@NotBlank String url, SummaryLength summaryLength, Boolean useLens) {

    public SummaryLength summaryLengthOrDefault() {
        return summaryLength == null ? SummaryLength.MEDIUM : summaryLength;
    }
}
