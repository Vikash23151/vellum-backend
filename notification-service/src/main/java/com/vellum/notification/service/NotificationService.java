package com.vellum.notification.service;

import com.vellum.notification.dto.*;
import com.vellum.notification.entity.Notification;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.Map;

public interface NotificationService {

    // User-facing operations
    NotificationResponse createNotification(
            Integer recipientId,
            Integer actorId,
            Notification.NotificationType type,
            String title,
            String message,
            Integer relatedId,
            String relatedType);

    Page<NotificationResponse> getNotifications(
            Integer userId, int page, int size);

    List<NotificationResponse> getUnreadNotifications(Integer userId);

    long getUnreadCount(Integer userId);

    void markAsRead(Integer notificationId, Integer userId);

    void markAllAsRead(Integer userId);

    void deleteNotification(Integer notificationId, Integer userId);

    void deleteReadNotifications(Integer userId);

    // Admin operations
    void sendBulkNotification(SendBulkNotificationRequest request,
                              List<Integer> allUserIds);

    // RabbitMQ event handlers
    void handleCommentEvent(Map<String, Object> payload);

    void handlePostPublishedEvent(Map<String, Object> payload);

    // Scheduled cleanup
    void cleanupOldNotifications();
}