package org.timmy.knowcommute.figure;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
@EnableConfigurationProperties({FigureProperties.class, FigureDebugProperties.class})
public class FigureConfig {

    FigureConfig(FigureProperties properties, FigureDebugProperties debugProperties) {
        if (properties.enabled() && (properties.model() == null || properties.model().isBlank())) {
            throw new IllegalStateException("readcast.figures.enabled=true requires READCAST_VISION_MODEL");
        }
        if (properties.enabled()) {
            log.info("Figure vision enabled: model={}, maxPerArticle={}",
                    properties.model(), properties.maxPerArticle());
        } else {
            log.info("Figure vision disabled: preserving caption and alt markup only");
        }
        if (debugProperties.enabled()) {
            log.info("Figure debug tracing: directory={}",
                    java.nio.file.Path.of(debugProperties.directory()).toAbsolutePath());
        } else {
            log.info("Figure debug tracing disabled");
        }
    }
}
