package com.vellum.newsletter.resource;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.vellum.newsletter.dto.*;
import com.vellum.newsletter.exception.CustomException;
import com.vellum.newsletter.exception.GlobalExceptionHandler;
import com.vellum.newsletter.service.NewsletterService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("NewsletterResource Controller Tests")
class NewsletterResourceTest {

    private MockMvc mockMvc;

    @Mock
    private NewsletterService newsletterService;

    @InjectMocks
    private NewsletterResource newsletterResource;

    private final ObjectMapper objectMapper =
            new ObjectMapper().registerModule(new JavaTimeModule());

    private SubscriberResponse sampleSubscriber;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(newsletterResource)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        sampleSubscriber = SubscriberResponse.builder()
                .subscriberId(1)
                .email("user@test.com")
                .fullName("Test User")
                .status("PENDING")
                .preferences("")
                .build();
    }

    // subscribe tests

    @Test
    @DisplayName("POST /subscribe: 200 OK for valid email")
    void subscribe_validEmail_returns200() throws Exception {
        SubscribeRequest request = new SubscribeRequest();
        request.setEmail("user@test.com");
        request.setFullName("Test User");

        when(newsletterService.subscribe(any(SubscribeRequest.class)))
                .thenReturn(sampleSubscriber);

        mockMvc.perform(post("/api/newsletter/subscribe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("POST /subscribe: 400 for invalid email")
    void subscribe_invalidEmail_returns400() throws Exception {
        SubscribeRequest request = new SubscribeRequest();
        request.setEmail("notanemail");

        mockMvc.perform(post("/api/newsletter/subscribe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.email").exists());
    }

    @Test
    @DisplayName("POST /subscribe: 409 for already active subscriber")
    void subscribe_alreadyActive_returns409() throws Exception {
        SubscribeRequest request = new SubscribeRequest();
        request.setEmail("active@test.com");

        when(newsletterService.subscribe(any()))
                .thenThrow(new CustomException(
                        "Already subscribed", HttpStatus.CONFLICT));

        mockMvc.perform(post("/api/newsletter/subscribe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }

    // confirm tests

    @Test
    @DisplayName("GET /confirm/{token}: 200 OK for valid token")
    void confirm_validToken_returns200() throws Exception {
        SubscriberResponse confirmed = SubscriberResponse.builder()
                .subscriberId(1).email("user@test.com")
                .status("ACTIVE").build();

        when(newsletterService.confirmSubscription("valid-token"))
                .thenReturn(confirmed);

        mockMvc.perform(get("/api/newsletter/confirm/valid-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("GET /confirm/{token}: 404 for invalid token")
    void confirm_invalidToken_returns404() throws Exception {
        when(newsletterService.confirmSubscription("bad-token"))
                .thenThrow(new CustomException(
                        "Invalid link", HttpStatus.NOT_FOUND));

        mockMvc.perform(get("/api/newsletter/confirm/bad-token"))
                .andExpect(status().isNotFound());
    }

    // unsubscribe tests

    @Test
    @DisplayName("GET /unsubscribe/{token}: 200 OK")
    void unsubscribe_returns200() throws Exception {
        doNothing().when(newsletterService)
                .unsubscribe("valid-token");

        mockMvc.perform(get("/api/newsletter/unsubscribe/valid-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").exists());
    }

    // admin tests

    @Test
    @DisplayName("GET /admin/stats: 200 OK for ADMIN")
    void getStats_admin_returns200() throws Exception {
        when(newsletterService.getStats())
                .thenReturn(Map.of(
                        "total", 100L, "active", 70L,
                        "pending", 20L, "unsubscribed", 10L));

        mockMvc.perform(get("/api/newsletter/admin/stats")
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(100))
                .andExpect(jsonPath("$.active").value(70));
    }

    @Test
    @DisplayName("GET /admin/stats: 403 for non-ADMIN")
    void getStats_nonAdmin_returns403() throws Exception {
        mockMvc.perform(get("/api/newsletter/admin/stats")
                        .header("X-User-Role", "AUTHOR"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /admin/send: 200 OK starts async campaign")
    void sendNewsletter_admin_returns200() throws Exception {
        SendNewsletterRequest request = new SendNewsletterRequest();
        request.setSubject("Monthly Update");
        request.setContent("<p>Hello readers!</p>");

        doNothing().when(newsletterService)
                .sendNewsletter(any(SendNewsletterRequest.class));

        mockMvc.perform(post("/api/newsletter/admin/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-User-Role", "ADMIN")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("GET /admin/subscribers: 200 OK returns list")
    void getAllSubscribers_admin_returns200() throws Exception {
        when(newsletterService.getAllSubscribers())
                .thenReturn(List.of(sampleSubscriber));

        mockMvc.perform(get("/api/newsletter/admin/subscribers")
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].email")
                        .value("user@test.com"));
    }
}