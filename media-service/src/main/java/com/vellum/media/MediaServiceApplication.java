package com.vellum.media;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class MediaServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(MediaServiceApplication.class, args);
        System.out.println("""
                ╔══════════════════════════════════════╗
                ║   VELLUM MEDIA SERVICE STARTED       ║
                ║   Port   : 8085                      ║
                ║   Swagger: /swagger-ui.html          ║
                ║   Health : /actuator/health          ║
                ╚══════════════════════════════════════╝
                """);
    }

}
