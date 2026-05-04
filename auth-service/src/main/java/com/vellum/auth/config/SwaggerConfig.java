package com.vellum.auth.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI vellumAuthOpenAPI() {

        SecurityScheme securityScheme = new SecurityScheme()
                .name("bearerAuth")
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT")
                .description("Enter your JWT token (without 'Bearer ' prefix)");


        SecurityRequirement securityRequirement =
                new SecurityRequirement().addList("bearerAuth");

        return new OpenAPI()
                .info(new Info()
                        .title("Vellum Auth Service API")
                        .description("""
                                Authentication and User Management API for Vellum Blogging Platform.
                                
                                ## How to authenticate:
                                1. POST /api/auth/register to create account
                                2. POST /api/auth/login to get JWT token
                                3. Click 'Authorize' button above
                                4. Enter the token (without 'Bearer ' prefix)
                                5. All subsequent requests will include the token
                                """)
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("Vellum Team")
                                .email("dev@vellum.com"))
                        .license(new License()
                                .name("MIT License")))
                .components(new Components()
                        .addSecuritySchemes("bearerAuth", securityScheme))
                .addSecurityItem(securityRequirement);
    }
}