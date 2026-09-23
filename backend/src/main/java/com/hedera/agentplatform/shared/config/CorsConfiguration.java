package com.hedera.agentplatform.shared.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Allows the dev frontend to call the API.
 *
 * <p>Implemented with {@link WebMvcConfigurer} rather than a lone {@code CorsConfigurationSource}
 * bean: without Spring Security that bean is never picked up, so preflight requests were answered
 * with 403 "Invalid CORS request" and the browser reported "Failed to fetch".
 */
@Configuration
public class CorsConfiguration implements WebMvcConfigurer {

    private final String[] allowedOrigins;

    public CorsConfiguration(
            @Value("${app.cors.allowed-origins:http://localhost:5173,http://127.0.0.1:5173}")
                    String[] allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .maxAge(3600);
    }
}
