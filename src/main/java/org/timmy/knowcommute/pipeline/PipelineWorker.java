package org.timmy.knowcommute.pipeline;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PipelineWorker {

    private final BookmarkClaimer claimer;
    private final BookmarkPipeline pipeline;
    private final PipelineProperties properties;

    @Qualifier("pipelineExecutor")
    private final ExecutorService executor;

    /** @return how many bookmarks were claimed, so callers and tests can see whether work ran. */
    public int runOnce() {
        Instant now = Instant.now();
        List<UUID> claimed = claimer.claim(
                properties.batchSize(), now, now.plus(properties.lease()));
        if (claimed.isEmpty()) {
            return 0;
        }

        log.debug("Claimed {} bookmark(s) for processing", claimed.size());
        CompletableFuture.allOf(claimed.stream()
                .map(id -> CompletableFuture.runAsync(() -> pipeline.process(id), executor))
                .toArray(CompletableFuture[]::new))
                .join();
        return claimed.size();
    }
}
