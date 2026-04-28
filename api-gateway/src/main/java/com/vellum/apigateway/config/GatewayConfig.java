package com.vellum.apigateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/*
 * application.yml globalcors handles route-level CORS.
 * This Java config handles it at the WebFilter level (lower level).
 * Having both ensures CORS headers are added even on error responses
 * (like 401 Unauthorized) which yml config sometimes misses.
 *
 * Without CORS headers on error responses:
 * → Browser gets "CORS error" instead of "401 Unauthorized"
 * → You can't tell what went wrong — very confusing to debug
 */
@Configuration
public class GatewayConfig {

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        // Which origins can call our API
        // In production: replace with actual domain
        config.setAllowedOrigins(List.of("http://localhost:4200"));

        // Which HTTP methods are allowed
        config.setAllowedMethods(Arrays.asList(
                "GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"
        ));

        // Allow ALL headers (including Authorization with JWT token)
        config.setAllowedHeaders(List.of("*"));

        // Allow cookies (needed if we ever use session-based auth)
        config.setAllowCredentials(true);

        // Browser caches preflight response for 1 hour
        // Preflight = browser's OPTIONS request before actual request
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}