package org.timmy.knowcommute.extraction;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(ExtractionProperties.class)
public class ExtractionConfig {
}
