package com.vellum.notification.config;

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
    public OpenAPI vellumNotificationOpenAPI() {
        SecurityScheme scheme = new SecurityScheme()
                .name("bearerAuth")
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT");

        return new OpenAPI()
                .info(new Info()
                        .title("Vellum Notification Service API")
                        .description("""
                                In-App Notification Management API.

                                ## All endpoints require JWT.

                                ## User endpoints:
                                - GET /api/notifications (paginated list)
                                - GET /api/notifications/unread
                                - GET /api/notifications/unread-count
                                - PUT /api/notifications/{id}/read
                                - PUT /api/notifications/read-all
                                - DELETE /api/notifications/{id}
                                - DELETE /api/notifications/read

                                ## Admin endpoints:
                                - POST /api/notifications/bulk
                                - GET /api/notifications/admin/all
                                """)
                        .version("1.0.0"))
                .components(new Components()
                        .addSecuritySchemes("bearerAuth", scheme))
                .addSecurityItem(
                        new SecurityRequirement().addList("bearerAuth"));
    }
}