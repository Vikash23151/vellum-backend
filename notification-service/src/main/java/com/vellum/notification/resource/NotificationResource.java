package com.vellum.notification.resource;

import com.vellum.notification.dto.*;
import com.vellum.notification.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
@Tag(name = "Notifications",
        description = "In-app notification management")
public class NotificationResource {

    private final NotificationService notificationService;

    // GET MY NOTIFICATIONS (PAGINATED)

    @GetMapping
    @Operation(summary = "Get my notifications (paginated)")
    public ResponseEntity<Page<NotificationResponse>> getNotifications(
            @RequestHeader("X-User-Id") Integer userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        return ResponseEntity.ok(
                notificationService.getNotifications(
                        userId, page, size));
    }

    // GET UNREAD NOTIFICATIONS

    @GetMapping("/unread")
    @Operation(summary = "Get unread notifications")
    public ResponseEntity<List<NotificationResponse>> getUnread(
            @RequestHeader("X-User-Id") Integer userId) {

        return ResponseEntity.ok(
                notificationService.getUnreadNotifications(userId));
    }

    // GET UNREAD COUNT

    @GetMapping("/unread-count")
    @Operation(summary = "Get unread notification count (for badge)")
    public ResponseEntity<Map<String, Long>> getUnreadCount(
            @RequestHeader("X-User-Id") Integer userId) {

        long count = notificationService.getUnreadCount(userId);
        return ResponseEntity.ok(Map.of("count", count));
    }

    // MARK SINGLE AS READ

    @PutMapping("/{notificationId}/read")
    @Operation(summary = "Mark a notification as read")
    public ResponseEntity<Map<String, String>> markAsRead(
            @PathVariable Integer notificationId,
            @RequestHeader("X-User-Id") Integer userId) {

        notificationService.markAsRead(notificationId, userId);
        return ResponseEntity.ok(
                Map.of("message", "Notification marked as read"));
    }

    // MARK ALL AS READ

    @PutMapping("/read-all")
    @Operation(summary = "Mark all notifications as read")
    public ResponseEntity<Map<String, String>> markAllAsRead(
            @RequestHeader("X-User-Id") Integer userId) {

        notificationService.markAllAsRead(userId);
        return ResponseEntity.ok(
                Map.of("message", "All notifications marked as read"));
    }

    // DELETE SINGLE NOTIFICATION
    @DeleteMapping("/{notificationId}")
    @Operation(summary = "Delete a specific notification")
    public ResponseEntity<Map<String, String>> delete(
            @PathVariable Integer notificationId,
            @RequestHeader("X-User-Id") Integer userId) {

        notificationService.deleteNotification(
                notificationId, userId);
        return ResponseEntity.ok(
                Map.of("message", "Notification deleted"));
    }

    // DELETE ALL READ NOTIFICATIONS

    @DeleteMapping("/read")
    @Operation(summary = "Delete all read notifications")
    public ResponseEntity<Map<String, String>> deleteRead(
            @RequestHeader("X-User-Id") Integer userId) {

        notificationService.deleteReadNotifications(userId);
        return ResponseEntity.ok(
                Map.of("message", "Read notifications cleared"));
    }

    // SEND BULK NOTIFICATION (ADMIN)

    @PostMapping("/bulk")
    @Operation(summary = "Send bulk notification (Admin only)")
    public ResponseEntity<Map<String, String>> sendBulk(
            @Valid @RequestBody SendBulkNotificationRequest request,
            @RequestHeader("X-User-Role") String userRole,
            @RequestHeader("X-User-Id") Integer adminId) {

        if (!"ADMIN".equals(userRole)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error",
                            "Admin access required"));
        }

        List<Integer> targetIds = request.getRecipientIds() != null
                ? request.getRecipientIds()
                : List.of();

        notificationService.sendBulkNotification(request, targetIds);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("message",
                        "Bulk notification sent successfully"));
    }
}