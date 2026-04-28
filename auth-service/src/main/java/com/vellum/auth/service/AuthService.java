package com.vellum.auth.service;

import com.vellum.auth.dto.*;
import com.vellum.auth.entity.User;
import java.util.List;

public interface AuthService {

    User register(RegisterRequest request);

    LoginResponse login(LoginRequest request);

    void logout(String token);

    boolean validateToken(String token);

    String refreshToken(String token);

    User getUserByEmail(String email);

    User getUserById(Integer userId);

    User updateProfile(Integer userId, UpdateProfileRequest request);

    void changePassword(Integer userId, ChangePasswordRequest request);

    List<User> searchUsers(String query);

    void deactivateAccount(Integer userId);

    void changeUserRole(Integer userId, User.Role newRole);

    List<User> getAllUsers();

    List<User> getUsersByRole(User.Role role);
}