package org.timmy.knowcommute.figure;

public record FigureDebugRunSummary(
        String id,
        String title,
        String articleUrl,
        String updatedAt,
        int figures,
        int described,
        int none,
        int failed,
        int cached,
        boolean reusedSummary,
        boolean capturedDecisions) {
}
