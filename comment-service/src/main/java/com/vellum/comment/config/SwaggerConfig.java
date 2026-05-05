package com.vellum.comment.config;

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
    public OpenAPI vellumCommentOpenAPI() {
        SecurityScheme securityScheme = new SecurityScheme()
                .name("bearerAuth")
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT");

        return new OpenAPI()
                .info(new Info()
                        .title("Vellum Comment Service API")
                        .description("""
                                Threaded Comment Management API.

                                ## Public endpoints (no auth):
                                - GET /api/comments/post/{postId}
                                - GET /api/comments/{commentId}/replies

                                ## Protected endpoints (JWT required):
                                - POST /api/comments (add comment)
                                - PUT /api/comments/{id} (edit)
                                - DELETE /api/comments/{id}
                                - POST /api/comments/{id}/like

                                ## Author/Admin endpoints:
                                - PUT /api/comments/{id}/approve
                                - PUT /api/comments/{id}/reject
                                """)
                        .version("1.0.0"))
                .components(new Components()
                        .addSecuritySchemes("bearerAuth", securityScheme))
                .addSecurityItem(new SecurityRequirement()
                        .addList("bearerAuth"));
    }
}
