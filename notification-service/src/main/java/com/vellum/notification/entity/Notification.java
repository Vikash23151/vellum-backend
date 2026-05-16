package com.vellum.notification.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "notifications",
        indexes = {
                /*
                 * Most critical index: fetch all notifications for a user.
                 * Every page load hits this. Must be fast.
                 */
                @Index(name = "idx_notif_recipient",
                        columnList = "recipient_id"),
                /*
                 * Most critical sub-query: unread count for badge.
                 * Angular polls this every 30 seconds.
                 * Composite index on (recipient_id, is_read) is optimal.
                 */
                @Index(name = "idx_notif_recipient_read",
                        columnList = "recipient_id, is_read"),
                @Index(name = "idx_notif_type",
                        columnList = "type"),
                @Index(name = "idx_notif_created",
                        columnList = "created_at")
        }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "notification_id")
    private Integer notificationId;

    /*
     * recipientId: user who receives this notification.
     * This user sees it in their notification bell.
     */
    @Column(name = "recipient_id", nullable = false)
    private Integer recipientId;

    /*
     * actorId: user who triggered the action.
     * null for system notifications (no specific actor).
     */
    @Column(name = "actor_id")
    private Integer actorId;

    /*
     * type: categorizes the notification.
     * Used for:
     * - Filtering notifications by type
     * - Displaying correct icon in UI
     * - Future: allowing users to mute specific types
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private NotificationType type;

    /*
     * title: short notification text.
     * Shown in dropdown list.
     */
    @Column(nullable = false, length = 300)
    private String title;

    /*
     * message: longer notification detail.
     * Shown on notification page / expanded view.
     */
    @Column(columnDefinition = "TEXT")
    private String message;

    /*
     * relatedId: ID of the related entity.
     * With relatedType, forms a polymorphic reference.
     * Examples: postId, commentId, userId
     */
    @Column(name = "related_id")
    private Integer relatedId;

    /*
     * relatedType: what relatedId refers to.
     * Examples: "POST", "COMMENT", "USER"
     * Angular uses this to build the deep-link URL:
     * POST    → /blog/{slug}
     * COMMENT → /blog/{slug}#comment-{id}
     */
    @Column(name = "related_type", length = 50)
    private String relatedType;

    /*
     * isRead: has the recipient seen this notification?
     * false (default) = unread → contributes to badge count
     * true            = read   → doesn't count for badge
     */
    @Column(name = "is_read", nullable = false)
    @Builder.Default
    private boolean isRead = false;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public enum NotificationType {
        NEW_COMMENT,    // Someone commented on your post
        COMMENT_REPLY,  // Someone replied to your comment
        MENTION,        // Someone @mentioned you
        NEW_POST,       // Author you follow published a post
        LIKE,           // Someone liked your post/comment
        SYSTEM          // Admin broadcast message
    }
}