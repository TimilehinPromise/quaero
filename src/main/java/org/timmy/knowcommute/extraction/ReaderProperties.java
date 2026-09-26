package org.timmy.knowcommute.extraction;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The hosted reader fallback for pages with no readable text in their markup. The provider
 * name selects the {@link PageReader} implementation; credentials and provider-specific
 * knobs live under {@code readcast.reader.<provider>.*}, so switching providers changes
 * configuration, not pipeline code.
 */
@ConfigurationProperties(prefix = "readcast.reader")
public record ReaderProperties(
        boolean enabled,
        String provider,
        Duration timeout,
        int maxResponseBytes) {
}
