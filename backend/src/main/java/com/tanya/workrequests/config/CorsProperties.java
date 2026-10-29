package com.tanya.workrequests.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.List;

/**
 * {@code app.cors.allowed-origins}: the front-end origins allowed to call the API. Set
 * {@code APP_CORS_ALLOWED_ORIGINS} (comma-separated) per environment.
 */
@ConfigurationProperties("app.cors")
public record CorsProperties(@DefaultValue("http://localhost:5173") List<String> allowedOrigins) {

    public CorsProperties {
        allowedOrigins = List.copyOf(allowedOrigins);
        // "Only the front-end origin" is the rule; a wildcard would quietly open the API to any site.
        if (allowedOrigins.stream().anyMatch(origin -> origin.contains("*"))) {
            throw new IllegalArgumentException("app.cors.allowed-origins must list exact origins, not wildcards");
        }
    }
}
