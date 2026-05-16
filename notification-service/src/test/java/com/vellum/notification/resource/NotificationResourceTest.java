package com.vellum.notification.resource;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.vellum.notification.dto.*;
import com.vellum.notification.exception.CustomException;
import com.vellum.notification.exception.GlobalExceptionHandler;
import com.vellum.notification.service.NotificationService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationResource Controller Tests")
class NotificationResourceTest {

    private MockMvc mockMvc;

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private NotificationResource notificationResource;

    private final ObjectMapper objectMapper =
            new ObjectMapper().registerModule(new JavaTimeModule());

    private NotificationResponse sampleNotification;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(notificationResource)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(
                        new PageableHandlerMethodArgumentResolver())
                .build();

        sampleNotification = NotificationResponse.builder()
                .notificationId(1)
                .recipientId(10)
                .actorId(20)
                .type("NEW_COMMENT")
                .typeLabel("New Comment")
                .typeIcon("💬")
                .title("New comment on your post")
                .message("Great article!")
                .relatedId(5)
                .relatedType("POST")
                .isRead(false)
                .timeAgo("just now")
                .createdAt(LocalDateTime.now())
                .build();
    }

    // GET /api/notifications tests

    @Test
    @DisplayName("GET /api/notifications: 200 OK returns paginated")
    void getNotifications_returns200() throws Exception {
        var page = new PageImpl<>(
                List.of(sampleNotification),
                PageRequest.of(0, 20),
                1);

        when(notificationService.getNotifications(10, 0, 20))
                .thenReturn(page);

        mockMvc.perform(get("/api/notifications")
                        .header("X-User-Id", "10")
                        .param("page", "0")
                        .param("size", "20"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].notificationId")
                        .value(1))
                .andExpect(jsonPath("$.content[0].typeIcon")
                        .value("💬"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    // GET /api/notifications/unread-count tests

    @Test
    @DisplayName("GET /unread-count: 200 OK returns count")
    void getUnreadCount_returns200() throws Exception {
        when(notificationService.getUnreadCount(10))
                .thenReturn(5L);

        mockMvc.perform(get("/api/notifications/unread-count")
                        .header("X-User-Id", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(5));
    }

    // GET /api/notifications/unread tests

    @Test
    @DisplayName("GET /unread: 200 OK returns unread notifications")
    void getUnread_returns200() throws Exception {
        when(notificationService.getUnreadNotifications(10))
                .thenReturn(List.of(sampleNotification));

        mockMvc.perform(get("/api/notifications/unread")
                        .header("X-User-Id", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].read").value(false));
    }

    // PUT /api/notifications/{id}/read tests

    @Test
    @DisplayName("PUT /1/read: 200 OK marks as read")
    void markAsRead_returns200() throws Exception {
        doNothing().when(notificationService).markAsRead(1, 10);

        mockMvc.perform(put("/api/notifications/1/read")
                        .header("X-User-Id", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message")
                        .value("Notification marked as read"));
    }

    @Test
    @DisplayName("PUT /1/read: 404 when not owner")
    void markAsRead_notOwner_returns404() throws Exception {
        doThrow(new CustomException(
                "Not found", HttpStatus.NOT_FOUND))
                .when(notificationService).markAsRead(1, 99);

        mockMvc.perform(put("/api/notifications/1/read")
                        .header("X-User-Id", "99"))
                .andExpect(status().isNotFound());
    }

    // PUT /api/notifications/read-all tests

    @Test
    @DisplayName("PUT /read-all: 200 OK marks all as read")
    void markAllAsRead_returns200() throws Exception {
        doNothing().when(notificationService).markAllAsRead(10);

        mockMvc.perform(put("/api/notifications/read-all")
                        .header("X-User-Id", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message")
                        .value("All notifications marked as read"));
    }

    // DELETE /api/notifications/{id} tests

    @Test
    @DisplayName("DELETE /1: 200 OK deletes notification")
    void deleteNotification_returns200() throws Exception {
        doNothing().when(notificationService)
                .deleteNotification(1, 10);

        mockMvc.perform(delete("/api/notifications/1")
                        .header("X-User-Id", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message")
                        .value("Notification deleted"));
    }

    // DELETE /api/notifications/read tests

    @Test
    @DisplayName("DELETE /read: 200 OK clears read notifications")
    void deleteRead_returns200() throws Exception {
        doNothing().when(notificationService)
                .deleteReadNotifications(10);

        mockMvc.perform(delete("/api/notifications/read")
                        .header("X-User-Id", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message")
                        .value("Read notifications cleared"));
    }

    // POST /api/notifications/bulk tests

    @Test
    @DisplayName("POST /bulk: 201 Created for ADMIN")
    void sendBulk_admin_returns201() throws Exception {
        SendBulkNotificationRequest request =
                new SendBulkNotificationRequest();
        request.setTitle("System maintenance");
        request.setMessage("Down at 2AM");
        request.setRecipientIds(List.of(1, 2, 3));

        doNothing().when(notificationService)
                .sendBulkNotification(
                        any(SendBulkNotificationRequest.class),
                        anyList());

        mockMvc.perform(post("/api/notifications/bulk")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-User-Id",   "99")
                        .header("X-User-Role", "ADMIN")
                        .content(objectMapper
                                .writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message")
                        .exists());
    }

    @Test
    @DisplayName("POST /bulk: 403 for non-ADMIN")
    void sendBulk_nonAdmin_returns403() throws Exception {
        SendBulkNotificationRequest request =
                new SendBulkNotificationRequest();
        request.setTitle("Test");

        mockMvc.perform(post("/api/notifications/bulk")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-User-Id",   "10")
                        .header("X-User-Role", "AUTHOR")
                        .content(objectMapper
                                .writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }
}