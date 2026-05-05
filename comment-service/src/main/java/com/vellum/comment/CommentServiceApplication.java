package com.vellum.comment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class CommentServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(CommentServiceApplication.class, args);
        System.out.println("""
                ╔══════════════════════════════════════╗
                ║   VELLUM COMMENT SERVICE STARTED     ║
                ║   Port   : 8083                      ║
                ║   Swagger: /swagger-ui.html          ║
                ║   Health : /actuator/health          ║
                ╚══════════════════════════════════════╝
                """);
    }

}
