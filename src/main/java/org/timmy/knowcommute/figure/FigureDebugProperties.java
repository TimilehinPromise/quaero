package org.timmy.knowcommute.figure;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "readcast.figures.debug")
public record FigureDebugProperties(String directory) {

    public boolean enabled() {
        return directory != null && !directory.isBlank();
    }
}
