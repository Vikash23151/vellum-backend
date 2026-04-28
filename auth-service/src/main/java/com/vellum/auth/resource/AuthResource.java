package com.vellum.auth.resource;

import com.vellum.auth.dto.*;
import com.vellum.auth.entity.User;
import com.vellum.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
@Tag(name = "Authentication", description = "User registration, login, and profile management")
public class AuthResource {

    private final AuthService authService;

    // REGISTER
    @PostMapping("/register")
    @Operation(summary = "Register new user", description = "Create a new reader account")
    public ResponseEntity<User> register(@Valid @RequestBody RegisterRequest request) {
        log.info("POST /api/auth/register - {}", request.getEmail());
        User user = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(user);
    }

    // LOGIN
    @PostMapping("/login")
    @Operation(summary = "User login", description = "Authenticate and receive JWT token")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        log.info("POST /api/auth/login - {}", request.getEmail());
        return ResponseEntity.ok(authService.login(request));
    }

    // LOGOUT
    @PostMapping("/logout")
    @Operation(summary = "Logout", description = "Invalidate the current session")
    public ResponseEntity<Map<String, String>> logout(
            @RequestHeader("Authorization") String authHeader) {
        String token = authHeader.replace("Bearer ", "");
        authService.logout(token);
        return ResponseEntity.ok(Map.of("message", "Logged out successfully"));
    }

    // REFRESH TOKEN
    @PostMapping("/refresh")
    @Operation(summary = "Refresh JWT token", description = "Get a new token before expiry")
    public ResponseEntity<Map<String, String>> refresh(
            @RequestBody Map<String, String> body) {
        String newToken = authService.refreshToken(body.get("token"));
        return ResponseEntity.ok(Map.of("token", newToken));
    }

    // VALIDATE TOKEN
    @GetMapping("/validate")
    @Operation(summary = "Validate JWT token")
    public ResponseEntity<Map<String, Boolean>> validate(
            @RequestParam String token) {
        boolean valid = authService.validateToken(token);
        return ResponseEntity.ok(Map.of("valid", valid));
    }

    // GET PROFILE
    @GetMapping("/profile/{userId}")
    @Operation(summary = "Get user profile")
    public ResponseEntity<User> getProfile(@PathVariable Integer userId) {
        return ResponseEntity.ok(authService.getUserById(userId));
    }

    // UPDATE PROFILE
    @PutMapping("/profile/{userId}")
    @Operation(summary = "Update user profile")
    public ResponseEntity<User> updateProfile(
            @PathVariable Integer userId,
            @Valid @RequestBody UpdateProfileRequest request) {
        return ResponseEntity.ok(authService.updateProfile(userId, request));
    }

    // CHANGE PASSWORD
    @PutMapping("/password/{userId}")
    @Operation(summary = "Change password")
    public ResponseEntity<Map<String, String>> changePassword(
            @PathVariable Integer userId,
            @Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(userId, request);
        return ResponseEntity.ok(Map.of("message", "Password changed successfully"));
    }

    // SEARCH USERS
    @GetMapping("/search")
    @Operation(summary = "Search users by username/email/name")
    public ResponseEntity<List<User>> searchUsers(
            @RequestParam String q) {
        return ResponseEntity.ok(authService.searchUsers(q));
    }

    // DEACTIVATE ACCOUNT
    @DeleteMapping("/deactivate/{userId}")
    @Operation(summary = "Deactivate user account")
    public ResponseEntity<Map<String, String>> deactivate(
            @PathVariable Integer userId) {
        authService.deactivateAccount(userId);
        return ResponseEntity.ok(Map.of("message", "Account deactivated"));
    }

    // CHANGE ROLE (Admin)
    @PutMapping("/role/{userId}")
    @Operation(summary = "Change user role (Admin only)")
    public ResponseEntity<Map<String, String>> changeRole(
            @PathVariable Integer userId,
            @RequestBody Map<String, String> body) {
        User.Role newRole = User.Role.valueOf(body.get("role").toUpperCase());
        authService.changeUserRole(userId, newRole);
        return ResponseEntity.ok(Map.of("message", "Role updated to " + newRole));
    }

    // GET ALL USERS (Admin)
    @GetMapping("/users")
    @Operation(summary = "Get all users (Admin only)")
    public ResponseEntity<List<User>> getAllUsers() {
        return ResponseEntity.ok(authService.getAllUsers());
    }

    // GET USER BY EMAIL
    @GetMapping("/by-email")
    @Operation(summary = "Get user by email")
    public ResponseEntity<User> getByEmail(@RequestParam String email) {
        return ResponseEntity.ok(authService.getUserByEmail(email));
    }
}