package com.vellum.newsletter.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SubscribeRequest {

    @NotBlank(message = "Email is required")
    @Email(message = "Please provide a valid email address")
    @Size(max = 200, message = "Email too long")
    private String email;

    @Size(max = 100, message = "Name too long")
    private String fullName;

    /*
     * preferences: optional comma-separated content preferences.
     * Example: "java,spring-boot,tutorials"
     * Used to send targeted newsletters.
     */
    @Size(max = 500, message = "Preferences too long")
    private String preferences;

    /*
     * userId: optional — if subscriber is a registered user.
     * Set by the controller from X-User-Id header (if authenticated).
     */
    private Integer userId;
}