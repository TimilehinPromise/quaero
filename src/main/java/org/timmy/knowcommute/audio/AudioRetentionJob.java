package org.timmy.knowcommute.audio;

import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "readcast.pipeline", name = "enabled", havingValue = "true")
public class AudioRetentionJob {

    private final AudioStorage storage;
    private final AudioProperties properties;

    @Scheduled(cron = "${readcast.audio.retention-cron}")
    public void deleteExpiredAudio() {
        Instant cutoff = Instant.now().minus(properties.retention());
        try {
            var expired = storage.keysOlderThan(cutoff);
            expired.forEach(storage::delete);
            if (!expired.isEmpty()) {
                log.info("Deleted {} audio object(s) older than {}", expired.size(), cutoff);
            }
        } catch (Exception e) {
            // Retention failing must not take the scheduler down; the next run retries.
            log.error("Audio retention sweep failed", e);
        }
    }
}
