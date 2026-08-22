package org.timmy.knowcommute.pipeline;

import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RetryPolicy {

    private static final int MAX_BACKOFF_SHIFT = 16;

    private final PipelineProperties properties;

    public boolean isExhausted(int attempts) {
        return attempts >= properties.maxAttempts();
    }

    public Instant nextAttemptAfter(int attempts, Instant from) {
        int shift = Math.min(Math.max(attempts - 1, 0), MAX_BACKOFF_SHIFT);
        Duration backoff = properties.initialBackoff().multipliedBy(1L << shift);
        return from.plus(backoff);
    }
}
