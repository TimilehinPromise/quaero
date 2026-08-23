package org.timmy.knowcommute.audio;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(AudioProperties.class)
public class AudioConfig {
}
