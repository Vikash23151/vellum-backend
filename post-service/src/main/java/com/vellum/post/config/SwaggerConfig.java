package com.vellum.post.config;

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
    public OpenAPI vellumPostOpenAPI() {
        SecurityScheme securityScheme = new SecurityScheme()
                .name("bearerAuth")
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT");

        return new OpenAPI()
                .info(new Info()
                        .title("Vellum Post Service API")
                        .description("""
                                Blog Post Management API.
                                
                                ## Public endpoints (no auth required):
                                - GET /api/posts (feed)
                                - GET /api/posts/slug/{slug}
                                - GET /api/posts/search
                                
                                ## Protected endpoints (JWT required):
                                - POST /api/posts (create)
                                - PUT /api/posts/{id} (update)
                                - PUT /api/posts/{id}/publish
                                - DELETE /api/posts/{id}
                                - POST /api/posts/{id}/like
                                """)
                        .version("1.0.0"))
                .components(new Components()
                        .addSecuritySchemes("bearerAuth", securityScheme))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }
}