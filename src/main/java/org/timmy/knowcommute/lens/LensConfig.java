package org.timmy.knowcommute.lens;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(LensProperties.class)
class LensConfig {
}
