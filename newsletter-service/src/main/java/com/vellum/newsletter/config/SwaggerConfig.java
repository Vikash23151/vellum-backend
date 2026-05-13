package com.vellum.newsletter.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI vellumNewsletterOpenAPI() {
        SecurityScheme scheme = new SecurityScheme()
                .name("bearerAuth")
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT");

        return new OpenAPI()
                .info(new Info()
                        .title("Vellum Newsletter Service API")
                        .description("""
                                Email Newsletter Management API.

                                ## Double Opt-In Flow:
                                1. POST /api/newsletter/subscribe
                                2. Check email for confirmation link
                                3. GET /api/newsletter/confirm/{token}
                                4. Now ACTIVE - receives campaigns

                                ## Public endpoints:
                                - POST /api/newsletter/subscribe
                                - GET /api/newsletter/confirm/{token}
                                - GET /api/newsletter/unsubscribe/{token}

                                ## Admin endpoints:
                                - GET /api/newsletter/admin/subscribers
                                - POST /api/newsletter/admin/send
                                - GET /api/newsletter/admin/stats
                                """)
                        .version("1.0.0"))
                .components(new Components()
                        .addSecuritySchemes("bearerAuth", scheme))
                .addSecurityItem(
                        new SecurityRequirement().addList("bearerAuth"));
    }
}