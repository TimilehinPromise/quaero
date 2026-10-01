package org.timmy.knowcommute.web;

import java.time.Duration;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.timmy.knowcommute.security.CurrentUserArgumentResolver;

@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(WebProperties.class)
public class WebMvcConfig implements WebMvcConfigurer {

    private final CurrentUserArgumentResolver currentUserArgumentResolver;
    private final WebProperties webProperties;

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(currentUserArgumentResolver);
    }

    /**
     * Named origins only, never a wildcard: the device header is the whole of this API's
     * identity, so any page allowed to call it can read and delete that device's library.
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        if (webProperties.allowedOrigins().isEmpty()) {
            return;
        }
        registry.addMapping("/api/**")
                .allowedOrigins(webProperties.allowedOrigins().toArray(String[]::new))
                .allowedMethods("GET", "POST", "PUT", "DELETE")
                .allowedHeaders("X-Device-Id", "Content-Type")
                .maxAge(Duration.ofHours(1).toSeconds());
    }
}
