package org.timmy.knowcommute.bookmark;

import jakarta.validation.constraints.NotBlank;

public record CreateBookmarkRequest(@NotBlank String url, SummaryLength summaryLength) {

    public SummaryLength summaryLengthOrDefault() {
        return summaryLength == null ? SummaryLength.MEDIUM : summaryLength;
    }
}
