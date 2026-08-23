package org.timmy.knowcommute.summary;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

@Configuration
@EnableConfigurationProperties(SummarizationProperties.class)
public class SummarizationConfig {

    /** Prompts live in a resource file: they change more often than code and belong in diffs. */
    @Bean
    Resource summarisePromptTemplate(@Value("classpath:prompts/summarise-article.st") Resource template) {
        return template;
    }
}
