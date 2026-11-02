package org.timmy.knowcommute.figure;

public record VisionResult(
        String knowledge, Integer promptTokens, Integer completionTokens, Integer totalTokens) {

    public boolean isNone() {
        return knowledge == null;
    }
}
