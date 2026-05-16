package com.vellum.notification.dto;

import com.vellum.notification.entity.Notification;
import lombok.*;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationResponse {

    private Integer notificationId;
    private Integer recipientId;
    private Integer actorId;
    private String  actorName;       // fetched by Angular from auth-service
    private String  actorAvatarUrl;  // fetched by Angular from auth-service
    private String  type;
    private String  typeLabel;       // human-readable: "New Comment"
    private String  typeIcon;        // emoji
    private String  title;
    private String  message;
    private Integer relatedId;
    private String  relatedType;
    private boolean isRead;
    private String  timeAgo;         // "2 hours ago"
    private LocalDateTime createdAt;

    public static NotificationResponse fromEntity(Notification n) {
        return NotificationResponse.builder()
                .notificationId(n.getNotificationId())
                .recipientId(n.getRecipientId())
                .actorId(n.getActorId())
                .type(n.getType().name())
                .typeLabel(getTypeLabel(n.getType()))
                .typeIcon(getTypeIcon(n.getType()))
                .title(n.getTitle())
                .message(n.getMessage())
                .relatedId(n.getRelatedId())
                .relatedType(n.getRelatedType())
                .isRead(n.isRead())
                .timeAgo(getTimeAgo(n.getCreatedAt()))
                .createdAt(n.getCreatedAt())
                .build();
    }

    /*
     * Human-readable type labels for UI display.
     */
    private static String getTypeLabel(
            Notification.NotificationType type) {
        return switch (type) {
            case NEW_COMMENT    -> "New Comment";
            case COMMENT_REPLY  -> "Reply";
            case MENTION        -> "Mention";
            case NEW_POST       -> "New Post";
            case LIKE           -> "Like";
            case SYSTEM         -> "System";
        };
    }

    /*
     * Emoji icons for notification types.
     * Used in notification dropdown for visual scanning.
     */
    private static String getTypeIcon(
            Notification.NotificationType type) {
        return switch (type) {
            case NEW_COMMENT    -> "💬";
            case COMMENT_REPLY  -> "↩️";
            case MENTION        -> "@";
            case NEW_POST       -> "📝";
            case LIKE           -> "❤️";
            case SYSTEM         -> "📢";
        };
    }

    /*
     * Relative time: "just now", "5 minutes ago", "2 hours ago".
     * Much friendlier than "2026-01-15T10:30:00".
     * Angular could also compute this client-side.
     * We compute server-side for consistency.
     */
    private static String getTimeAgo(LocalDateTime createdAt) {
        if (createdAt == null) return "";

        LocalDateTime now = LocalDateTime.now();
        long seconds = java.time.temporal.ChronoUnit.SECONDS
                .between(createdAt, now);

        if (seconds < 60)          return "just now";
        if (seconds < 3600)        return (seconds / 60) + " min ago";
        if (seconds < 86400)       return (seconds / 3600) + " hours ago";
        if (seconds < 86400 * 7)   return (seconds / 86400) + " days ago";
        if (seconds < 86400 * 30)  return (seconds / 86400 / 7) + " weeks ago";
        return (seconds / 86400 / 30) + " months ago";
    }
}