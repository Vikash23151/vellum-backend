package com.vellum.category.config;

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
    public OpenAPI vellumCategoryOpenAPI() {
        SecurityScheme scheme = new SecurityScheme()
                .name("bearerAuth")
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT");

        return new OpenAPI()
                .info(new Info()
                        .title("Vellum Category & Tag Service API")
                        .description("""
                                Category and Tag Taxonomy API.

                                ## Public endpoints:
                                - GET /api/categories (all)
                                - GET /api/categories/tree (hierarchical)
                                - GET /api/categories/{slug}
                                - GET /api/tags (all)
                                - GET /api/tags/trending
                                - GET /api/tags/post/{postId}

                                ## Admin-only endpoints:
                                - POST /api/categories
                                - PUT /api/categories/{id}
                                - DELETE /api/categories/{id}
                                - POST /api/tags
                                - DELETE /api/tags/{id}

                                ## Author endpoints:
                                - POST /api/tags/post/{postId}/tag/{tagId}
                                - DELETE /api/tags/post/{postId}/tag/{tagId}
                                """)
                        .version("1.0.0"))
                .components(new Components()
                        .addSecuritySchemes("bearerAuth", scheme))
                .addSecurityItem(
                        new SecurityRequirement().addList("bearerAuth"));
    }
}
