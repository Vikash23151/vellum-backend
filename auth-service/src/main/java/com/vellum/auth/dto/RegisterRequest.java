package com.vellum.auth.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

/*
 * Validation annotations:
 * @NotBlank   → not null AND not empty AND not just spaces
 * @NotNull    → not null (but can be empty string)
 * @Email      → must match email pattern (x@y.z)
 * @Size       → string length between min and max
 * @Min/@Max   → number value constraint
 * @Pattern    → must match regex
 */
@Data
public class RegisterRequest {

    @NotBlank(message = "Username is required")
    @Size(min = 3, max = 50, message = "Username must be between 3 and 50 characters")
    @Pattern(
            regexp = "^[a-zA-Z0-9_]+$",
            message = "Username can only contain letters, numbers and underscores"
    )
    private String username;

    @NotBlank(message = "Email is required")
    @Email(message = "Please provide a valid email address")
    @Size(max = 50, message = "Email cannot exceed 50 characters")
    private String email;

    @NotBlank(message = "Password is required")
    @Size(min = 8, max = 100, message = "Password must be at least 8 characters")
    private String password;

    @Size(max = 100, message = "Full name cannot exceed 100 characters")
    private String fullName;
}