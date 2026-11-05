package org.timmy.knowcommute.figure;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

@ConfigurationProperties(prefix = "readcast.figures.debug")
public record FigureDebugProperties(String directory, boolean viewerEnabled, String viewerToken) {

    @ConstructorBinding
    public FigureDebugProperties {
    }

    public FigureDebugProperties(String directory) {
        this(directory, false, "");
    }

    public boolean enabled() {
        return directory != null && !directory.isBlank();
    }
}
