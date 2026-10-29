package org.timmy.knowcommute.audio;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(TtsProperties.class)
public class SpeechModelConfig {

    @Bean
    CaptionedSpeechModel captionedSpeechModel(TtsProperties properties) {
        return new KokoroCaptionedSpeechModel(properties, new ObjectMapper());
    }
}
