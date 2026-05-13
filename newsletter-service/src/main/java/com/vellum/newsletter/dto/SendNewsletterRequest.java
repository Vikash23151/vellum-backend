package com.vellum.newsletter.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SendNewsletterRequest {

    @NotBlank(message = "Subject is required")
    @Size(max = 200, message = "Subject too long")
    private String subject;

    @NotBlank(message = "Content is required")
    private String content;  // HTML content

    /*
     * targetPreference: filter subscribers by preference.
     * null or blank = send to ALL active subscribers.
     * "java" = send only to subscribers with "java" preference.
     */
    private String targetPreference;
}