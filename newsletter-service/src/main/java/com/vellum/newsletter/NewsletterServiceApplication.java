package com.vellum.newsletter;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class NewsletterServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(NewsletterServiceApplication.class, args);
        System.out.println("""
                ╔══════════════════════════════════════╗
                ║  VELLUM NEWSLETTER SERVICE STARTED   ║
                ║  Port   : 8086                       ║
                ║  Swagger: /swagger-ui.html           ║
                ║  Health : /actuator/health           ║
                ╚══════════════════════════════════════╝
                """);
    }

}
