package com.vellum.category;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class CategoryServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(CategoryServiceApplication.class, args);
        System.out.println("""
                ╔══════════════════════════════════════╗
                ║  VELLUM CATEGORY SERVICE STARTED     ║
                ║  Port   : 8084                       ║
                ║  Swagger: /swagger-ui.html           ║
                ║  Health : /actuator/health           ║
                ╚══════════════════════════════════════╝
                """);
    }

}
