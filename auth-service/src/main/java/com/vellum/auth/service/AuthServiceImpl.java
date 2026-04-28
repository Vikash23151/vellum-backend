package com.vellum.auth.service;

import com.vellum.auth.config.JwtConfig;
import com.vellum.auth.dto.*;
import com.vellum.auth.entity.User;
import com.vellum.auth.exception.CustomException;
import com.vellum.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtConfig jwtConfig;

    @Override
    @Transactional
    public User register(RegisterRequest request) {
        log.info("Registering user: {}", request.getEmail());

        // Check email uniqueness
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new CustomException(
                    "Email '" + request.getEmail() + "' is already registered",
                    HttpStatus.CONFLICT
            );
        }

        // Check username uniqueness
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new CustomException(
                    "Username '" + request.getUsername() + "' is already taken",
                    HttpStatus.CONFLICT
            );
        }

        User user = User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .fullName(request.getFullName())
                .role(User.Role.READER)
                .provider(User.Provider.LOCAL)
                .isActive(true)
                .build();

        User savedUser = userRepository.save(user);
        log.info("User registered: {} (id: {})", savedUser.getEmail(), savedUser.getUserId());
        return savedUser;
    }

    @Override
    public LoginResponse login(LoginRequest request) {
        log.info("Login attempt: {}", request.getEmail());

        // Find user by email
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new CustomException(
                        "Invalid email or password",
                        HttpStatus.UNAUTHORIZED
                ));

        // Check if account is active
        if (!user.isActive()) {
            throw new CustomException(
                    "Your account has been deactivated. Contact support.",
                    HttpStatus.FORBIDDEN
            );
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new CustomException(
                    "Invalid email or password",
                    HttpStatus.UNAUTHORIZED
            );
        }

        // Generate JWT token
        String token = jwtConfig.generateToken(
                user.getEmail(),
                user.getRole().name(),
                user.getUserId()
        );

        log.info("Login successful: {} (role: {})", user.getEmail(), user.getRole());

        return LoginResponse.builder()
                .token(token)
                .userId(user.getUserId())
                .username(user.getUsername())
                .email(user.getEmail())
                .role(user.getRole().name())
                .fullName(user.getFullName())
                .avatarUrl(user.getAvatarUrl())
                .build();
    }

    @Override
    public void logout(String token) {
        log.info("User logged out (client-side token deletion)");
    }

    @Override
    public boolean validateToken(String token) {
        return jwtConfig.isTokenValid(token);
    }

    @Override
    public String refreshToken(String oldToken) {
        if (!jwtConfig.isTokenValid(oldToken)) {
            throw new CustomException("Cannot refresh: token is invalid", HttpStatus.UNAUTHORIZED);
        }

        String email  = jwtConfig.extractEmail(oldToken);
        String role   = jwtConfig.extractRole(oldToken);
        Integer userId = jwtConfig.extractUserId(oldToken);

        // Verify user still exists and is active
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new CustomException("User not found", HttpStatus.NOT_FOUND));

        if (!user.isActive()) {
            throw new CustomException("Account is deactivated", HttpStatus.FORBIDDEN);
        }

        return jwtConfig.generateToken(email, role, userId);
    }

    @Override
    public User getUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new CustomException(
                        "User not found with email: " + email,
                        HttpStatus.NOT_FOUND
                ));
    }

    @Override
    public User getUserById(Integer userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(
                        "User not found with id: " + userId,
                        HttpStatus.NOT_FOUND
                ));
    }

    @Override
    @Transactional
    public User updateProfile(Integer userId, UpdateProfileRequest request) {
        User user = getUserById(userId);

        // Only update fields that were provided (not null)
        if (request.getFullName()  != null) user.setFullName(request.getFullName());
        if (request.getBio()       != null) user.setBio(request.getBio());
        if (request.getAvatarUrl() != null) user.setAvatarUrl(request.getAvatarUrl());

        return userRepository.save(user);
    }

    @Override
    @Transactional
    public void changePassword(Integer userId, ChangePasswordRequest request) {
        User user = getUserById(userId);

        if (!passwordEncoder.matches(request.getOldPassword(), user.getPasswordHash())) {
            throw new CustomException("Current password is incorrect", HttpStatus.BAD_REQUEST);
        }

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
        log.info("Password changed for user: {}", userId);
    }

    @Override
    public List<User> searchUsers(String query) {
        return userRepository.searchUsers(query);
    }

    @Override
    @Transactional
    public void deactivateAccount(Integer userId) {
        User user = getUserById(userId);
        user.setActive(false);
        userRepository.save(user);
        log.info("Account deactivated: {}", userId);
    }

    @Override
    @Transactional
    public void changeUserRole(Integer userId, User.Role newRole) {
        User user = getUserById(userId);
        User.Role oldRole = user.getRole();
        user.setRole(newRole);
        userRepository.save(user);
        log.info("Role changed: userId={} {} → {}", userId, oldRole, newRole);
    }

    @Override
    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    @Override
    public List<User> getUsersByRole(User.Role role) {
        return userRepository.findAllByRole(role);
    }
}