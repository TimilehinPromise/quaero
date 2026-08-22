package org.timmy.knowcommute.pipeline;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@EnableScheduling
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "readcast.pipeline", name = "enabled", havingValue = "true")
public class PipelineScheduler {

    private final PipelineWorker worker;

    @Scheduled(fixedDelayString = "${readcast.pipeline.poll-interval}")
    public void poll() {
        try {
            worker.runOnce();
        } catch (Exception e) {
            // A poll failure must not kill the schedule; the next tick retries.
            log.error("Pipeline poll failed", e);
        }
    }
}
