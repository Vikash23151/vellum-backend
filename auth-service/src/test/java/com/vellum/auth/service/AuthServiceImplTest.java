package com.vellum.auth.service;

import com.vellum.auth.config.JwtConfig;
import com.vellum.auth.dto.*;
import com.vellum.auth.entity.User;
import com.vellum.auth.exception.CustomException;
import com.vellum.auth.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/*
 * UNIT TEST: Tests AuthServiceImpl IN ISOLATION.
 * All dependencies (UserRepository, JwtConfig, PasswordEncoder) are MOCKED.
 *
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AuthServiceImpl Unit Tests")
class AuthServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtConfig jwtConfig;

    @InjectMocks
    private AuthServiceImpl authService;

    // Reusable test data
    private User testUser;
    private RegisterRequest registerRequest;
    private LoginRequest loginRequest;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .userId(1)
                .username("johndoe")
                .email("john@vellum.com")
                .passwordHash("$2a$10$hashedPassword")
                .fullName("John Doe")
                .role(User.Role.READER)
                .provider(User.Provider.LOCAL)
                .isActive(true)
                .build();

        registerRequest = new RegisterRequest();
        registerRequest.setUsername("johndoe");
        registerRequest.setEmail("john@vellum.com");
        registerRequest.setPassword("password123");
        registerRequest.setFullName("John Doe");

        loginRequest = new LoginRequest();
        loginRequest.setEmail("john@vellum.com");
        loginRequest.setPassword("password123");
    }

    // register() tests

    @Test
    @DisplayName("register: success - new user created with READER role")
    void register_success_createsReaderUser() {
        // ARRANGE

        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(userRepository.existsByUsername(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$10$hashedPassword");
        when(userRepository.save(any(User.class))).thenReturn(testUser);

        // ACT
        User result = authService.register(registerRequest);

        // ASSERT
        assertThat(result).isNotNull();
        assertThat(result.getEmail()).isEqualTo("john@vellum.com");
        assertThat(result.getRole()).isEqualTo(User.Role.READER);


        verify(userRepository, times(1)).save(any(User.class));
        verify(passwordEncoder, times(1)).encode("password123");
    }

    @Test
    @DisplayName("register: fails when email already exists")
    void register_emailExists_throwsConflict() {
        // ARRANGE: simulate email already in use
        when(userRepository.existsByEmail("john@vellum.com")).thenReturn(true);

        // ACT + ASSERT

        assertThatThrownBy(() -> authService.register(registerRequest))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("already registered");

        // Verify repository.save() was NEVER called (no user created)
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("register: fails when username already taken")
    void register_usernameExists_throwsConflict() {
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(userRepository.existsByUsername("johndoe")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(registerRequest))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("already taken");

        verify(userRepository, never()).save(any(User.class));
    }

    // login() tests

    @Test
    @DisplayName("login: success - returns LoginResponse with token")
    void login_success_returnsTokenAndUserInfo() {
        // ARRANGE
        when(userRepository.findByEmail("john@vellum.com"))
                .thenReturn(Optional.of(testUser));

        when(passwordEncoder.matches("password123", "$2a$10$hashedPassword"))
                .thenReturn(true);

        when(jwtConfig.generateToken(anyString(), anyString(), anyInt()))
                .thenReturn("eyJhbGciOiJIUzI1NiJ9.testtoken");

        // ACT
        LoginResponse response = authService.login(loginRequest);

        // ASSERT
        assertThat(response).isNotNull();
        assertThat(response.getToken()).isEqualTo("eyJhbGciOiJIUzI1NiJ9.testtoken");
        assertThat(response.getEmail()).isEqualTo("john@vellum.com");
        assertThat(response.getRole()).isEqualTo("READER");
        assertThat(response.getUserId()).isEqualTo(1);
    }

    @Test
    @DisplayName("login: fails when user not found")
    void login_userNotFound_throwsUnauthorized() {
        // Simulate user not found in DB
        when(userRepository.findByEmail("john@vellum.com"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(loginRequest))
                .isInstanceOf(CustomException.class)
                .satisfies(ex -> {
                    CustomException customEx = (CustomException) ex;
                    assertThat(customEx.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
                });
    }

    @Test
    @DisplayName("login: fails when password is wrong")
    void login_wrongPassword_throwsUnauthorized() {
        when(userRepository.findByEmail("john@vellum.com"))
                .thenReturn(Optional.of(testUser));

        // Mock: wrong password doesn't match
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);

        assertThatThrownBy(() -> authService.login(loginRequest))
                .isInstanceOf(CustomException.class)
                .satisfies(ex -> assertThat(((CustomException) ex).getStatus())
                        .isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    @Test
    @DisplayName("login: fails when account is deactivated")
    void login_deactivatedAccount_throwsForbidden() {
        User deactivatedUser = User.builder()
                .userId(2)
                .username("deactivated")
                .email("john@vellum.com")
                .passwordHash("hash")
                .role(User.Role.READER)
                .provider(User.Provider.LOCAL)
                .isActive(false)  // ← deactivated
                .build();

        when(userRepository.findByEmail("john@vellum.com"))
                .thenReturn(Optional.of(deactivatedUser));

        assertThatThrownBy(() -> authService.login(loginRequest))
                .isInstanceOf(CustomException.class)
                .satisfies(ex -> assertThat(((CustomException) ex).getStatus())
                        .isEqualTo(HttpStatus.FORBIDDEN));
    }

    // getUserById() tests

    @Test
    @DisplayName("getUserById: success - returns user when found")
    void getUserById_found_returnsUser() {
        when(userRepository.findById(1)).thenReturn(Optional.of(testUser));

        User result = authService.getUserById(1);

        assertThat(result).isNotNull();
        assertThat(result.getUserId()).isEqualTo(1);
    }

    @Test
    @DisplayName("getUserById: throws NOT_FOUND when user doesn't exist")
    void getUserById_notFound_throwsException() {
        when(userRepository.findById(999)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.getUserById(999))
                .isInstanceOf(CustomException.class)
                .satisfies(ex -> assertThat(((CustomException) ex).getStatus())
                        .isEqualTo(HttpStatus.NOT_FOUND));
    }

    // updateProfile() tests

    @Test
    @DisplayName("updateProfile: updates only provided fields")
    void updateProfile_updatesOnlyProvidedFields() {
        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setFullName("John Updated");
        // bio and avatarUrl are null → should NOT be updated

        when(userRepository.findById(1)).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        // .thenAnswer: return the same object that was passed to save()

        User result = authService.updateProfile(1, request);

        assertThat(result.getFullName()).isEqualTo("John Updated");
        // Bio should remain unchanged (was null in request)
        assertThat(result.getBio()).isNull();
    }

    // changePassword() tests

    @Test
    @DisplayName("changePassword: success when old password is correct")
    void changePassword_correct_updatesPassword() {
        ChangePasswordRequest request = new ChangePasswordRequest();
        request.setOldPassword("oldPass123");
        request.setNewPassword("newPass456");

        when(userRepository.findById(1)).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("oldPass123", testUser.getPasswordHash()))
                .thenReturn(true);
        when(passwordEncoder.encode("newPass456")).thenReturn("$2a$10$newHash");
        when(userRepository.save(any(User.class))).thenReturn(testUser);

        // Should not throw
        assertThatCode(() -> authService.changePassword(1, request))
                .doesNotThrowAnyException();

        verify(passwordEncoder, times(1)).encode("newPass456");
        verify(userRepository, times(1)).save(any(User.class));
    }

    @Test
    @DisplayName("changePassword: fails when old password is wrong")
    void changePassword_wrongOldPassword_throwsBadRequest() {
        ChangePasswordRequest request = new ChangePasswordRequest();
        request.setOldPassword("wrongPass");
        request.setNewPassword("newPass456");

        when(userRepository.findById(1)).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("wrongPass", testUser.getPasswordHash()))
                .thenReturn(false);

        assertThatThrownBy(() -> authService.changePassword(1, request))
                .isInstanceOf(CustomException.class)
                .satisfies(ex -> assertThat(((CustomException) ex).getStatus())
                        .isEqualTo(HttpStatus.BAD_REQUEST));

        // Ensure new password was NOT saved
        verify(userRepository, never()).save(any(User.class));
    }

    // searchUsers() tests

    @Test
    @DisplayName("searchUsers: returns matching users")
    void searchUsers_returnsResults() {
        when(userRepository.searchUsers("john")).thenReturn(List.of(testUser));

        List<User> results = authService.searchUsers("john");

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getUsername()).isEqualTo("johndoe");
    }

    // deactivateAccount() tests

    @Test
    @DisplayName("deactivateAccount: sets isActive to false")
    void deactivateAccount_setsInactive() {
        when(userRepository.findById(1)).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        authService.deactivateAccount(1);

        // Verify save was called with isActive = false
        verify(userRepository).save(argThat(user -> !user.isActive()));
    }
}