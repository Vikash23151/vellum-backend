package com.vellum.post;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class PostServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(PostServiceApplication.class, args);
        System.out.println("""
                ╔══════════════════════════════════════╗
                ║   VELLUM POST SERVICE STARTED        ║
                ║   Port   : 8082                      ║
                ║   Swagger: /swagger-ui.html          ║
                ║   Health : /actuator/health          ║
                ╚══════════════════════════════════════╝
                """);
    }

}
