package com.vellum.media.config;

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
    public OpenAPI vellumMediaOpenAPI() {
        SecurityScheme scheme = new SecurityScheme()
                .name("bearerAuth")
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT");

        return new OpenAPI()
                .info(new Info()
                        .title("Vellum Media Service API")
                        .description("""
                                File Upload and Management API.

                                ## All endpoints require authentication.

                                ## Upload Rules:
                                - Max file size: 10MB
                                - Allowed types: JPEG, PNG, GIF, WebP, PDF
                                - Files stored on AWS S3

                                ## Endpoints:
                                - POST /api/media/upload
                                - GET /api/media/my (my files)
                                - GET /api/media/{id}
                                - PUT /api/media/{id}/alt-text
                                - POST /api/media/{id}/link/{postId}
                                - DELETE /api/media/{id}/unlink
                                - DELETE /api/media/{id}
                                - GET /api/media/admin/all (Admin)
                                """)
                        .version("1.0.0"))
                .components(new Components()
                        .addSecuritySchemes("bearerAuth", scheme))
                .addSecurityItem(
                        new SecurityRequirement().addList("bearerAuth"));
    }
}