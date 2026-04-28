package com.vellum.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse {

    private String token;       // JWT token — Angular stores this
    private Integer userId;
    private String username;
    private String email;
    private String role;        // "READER", "AUTHOR", or "ADMIN"
    private String fullName;
    private String avatarUrl;
}