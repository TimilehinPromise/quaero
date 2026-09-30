package org.timmy.knowcommute.lens;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "readcast.lens")
public record LensProperties(Duration checkTimeout) {
}
