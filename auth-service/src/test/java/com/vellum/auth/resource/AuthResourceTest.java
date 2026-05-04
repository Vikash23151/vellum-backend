package com.vellum.auth.resource;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vellum.auth.dto.*;
import com.vellum.auth.entity.User;
import com.vellum.auth.exception.CustomException;
import com.vellum.auth.exception.GlobalExceptionHandler;
import com.vellum.auth.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.*;


@ExtendWith(MockitoExtension.class)
@DisplayName("AuthResource Controller Tests")
class AuthResourceTest {

    private MockMvc mockMvc;

    @Mock
    private AuthService authService;

    @InjectMocks
    private AuthResource authResource;


    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {

        mockMvc = MockMvcBuilders
                .standaloneSetup(authResource)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    // POST /api/auth/register tests

    @Test
    @DisplayName("POST /register: 201 Created on success")
    void register_validRequest_returns201() throws Exception {
        // ARRANGE
        RegisterRequest request = new RegisterRequest();
        request.setUsername("johndoe");
        request.setEmail("john@vellum.com");
        request.setPassword("password123");
        request.setFullName("John Doe");

        User createdUser = User.builder()
                .userId(1)
                .username("johndoe")
                .email("john@vellum.com")
                .role(User.Role.READER)
                .provider(User.Provider.LOCAL)
                .isActive(true)
                .build();

        when(authService.register(any(RegisterRequest.class))).thenReturn(createdUser);

        // ACT + ASSERT

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("john@vellum.com"))
                .andExpect(jsonPath("$.username").value("johndoe"))
                .andExpect(jsonPath("$.role").value("READER"));
    }

    @Test
    @DisplayName("POST /register: 400 Bad Request when username is blank")
    void register_blankUsername_returns400() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("");           // blank → validation fails
        request.setEmail("john@vellum.com");
        request.setPassword("password123");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.username").exists());
    }

    @Test
    @DisplayName("POST /register: 400 Bad Request when email is invalid")
    void register_invalidEmail_returns400() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("johndoe");
        request.setEmail("notanemail");  // invalid email format
        request.setPassword("password123");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.email").exists());
    }

    @Test
    @DisplayName("POST /register: 409 Conflict when email already exists")
    void register_duplicateEmail_returns409() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("johndoe");
        request.setEmail("john@vellum.com");
        request.setPassword("password123");

        // Mock service throws CustomException (conflict)
        when(authService.register(any(RegisterRequest.class)))
                .thenThrow(new CustomException("Email already registered", HttpStatus.CONFLICT));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Email already registered"));
    }

    // POST /api/auth/login tests

    @Test
    @DisplayName("POST /login: 200 OK with token on valid credentials")
    void login_validCredentials_returns200WithToken() throws Exception {
        LoginRequest request = new LoginRequest();
        request.setEmail("john@vellum.com");
        request.setPassword("password123");

        LoginResponse response = LoginResponse.builder()
                .token("eyJhbGc.testtoken")
                .userId(1)
                .username("johndoe")
                .email("john@vellum.com")
                .role("READER")
                .build();

        when(authService.login(any(LoginRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("eyJhbGc.testtoken"))
                .andExpect(jsonPath("$.role").value("READER"))
                .andExpect(jsonPath("$.userId").value(1));
    }

    @Test
    @DisplayName("POST /login: 401 Unauthorized on wrong credentials")
    void login_wrongPassword_returns401() throws Exception {
        LoginRequest request = new LoginRequest();
        request.setEmail("john@vellum.com");
        request.setPassword("wrongpassword");

        when(authService.login(any(LoginRequest.class)))
                .thenThrow(new CustomException("Invalid email or password", HttpStatus.UNAUTHORIZED));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Invalid email or password"));
    }

    @Test
    @DisplayName("POST /login: 400 Bad Request when email is blank")
    void login_blankEmail_returns400() throws Exception {
        LoginRequest request = new LoginRequest();
        request.setEmail("");  // blank
        request.setPassword("password123");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    // GET /api/auth/profile/{userId} tests

    @Test
    @DisplayName("GET /profile/1: 200 OK returns user profile")
    void getProfile_exists_returns200() throws Exception {
        User user = User.builder()
                .userId(1)
                .username("johndoe")
                .email("john@vellum.com")
                .role(User.Role.READER)
                .provider(User.Provider.LOCAL)
                .isActive(true)
                .build();

        when(authService.getUserById(1)).thenReturn(user);

        mockMvc.perform(get("/api/auth/profile/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(1))
                .andExpect(jsonPath("$.username").value("johndoe"));
    }

    @Test
    @DisplayName("GET /profile/999: 404 Not Found for unknown user")
    void getProfile_notExists_returns404() throws Exception {
        when(authService.getUserById(999))
                .thenThrow(new CustomException("User not found", HttpStatus.NOT_FOUND));

        mockMvc.perform(get("/api/auth/profile/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("User not found"));
    }

    // GET /api/auth/search tests

    @Test
    @DisplayName("GET /search?q=john: 200 OK returns list of users")
    void searchUsers_returns200WithList() throws Exception {
        User user = User.builder()
                .userId(1)
                .username("johndoe")
                .email("john@vellum.com")
                .role(User.Role.READER)
                .provider(User.Provider.LOCAL)
                .isActive(true)
                .build();

        when(authService.searchUsers("john")).thenReturn(java.util.List.of(user));

        mockMvc.perform(get("/api/auth/search").param("q", "john"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].username").value("johndoe"));
    }
}