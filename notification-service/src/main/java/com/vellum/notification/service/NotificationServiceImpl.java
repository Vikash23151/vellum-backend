package com.vellum.notification.service;

import com.vellum.notification.config.RabbitMQConfig;
import com.vellum.notification.dto.*;
import com.vellum.notification.entity.Notification;
import com.vellum.notification.exception.CustomException;
import com.vellum.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;

    @Value("${app.notification.retention-days:90}")
    private int retentionDays;

    @Value("${app.notification.max-per-user:100}")
    private int maxPerUser;

    // CREATE NOTIFICATION
    @Override
    @Transactional
    public NotificationResponse createNotification(
            Integer recipientId,
            Integer actorId,
            Notification.NotificationType type,
            String title,
            String message,
            Integer relatedId,
            String relatedType) {

        /*
         * Don't notify users about their OWN actions.
         * If author comments on own post → no notification to self.
         * If actorId == recipientId → skip.
         */
        if (actorId != null && actorId.equals(recipientId)) {
            log.debug("Skipping self-notification: userId={}",
                    actorId);
            return null;
        }

        /*
         * Enforce max-per-user limit.
         * If user already has 100 notifications:
         * Delete the oldest 1 before inserting the new one.
         * Keeps DB size bounded per user.
         */
        long currentCount = notificationRepository
                .countByRecipientId(recipientId);

        if (currentCount >= maxPerUser) {
            List<Notification> oldest = notificationRepository
                    .findOldestByRecipientId(
                            recipientId, PageRequest.of(0, 1));
            if (!oldest.isEmpty()) {
                notificationRepository.delete(oldest.get(0));
                log.debug("Removed oldest notification for " +
                                "user {} (limit={})",
                        recipientId, maxPerUser);
            }
        }

        Notification notification = Notification.builder()
                .recipientId(recipientId)
                .actorId(actorId)
                .type(type)
                .title(title)
                .message(message)
                .relatedId(relatedId)
                .relatedType(relatedType)
                .isRead(false)
                .build();

        Notification saved =
                notificationRepository.save(notification);
        log.debug("Notification created: id={} type={} " +
                        "recipient={}",
                saved.getNotificationId(), type, recipientId);

        return NotificationResponse.fromEntity(saved);
    }

    // GET NOTIFICATIONS (PAGINATED)
    @Override
    public Page<NotificationResponse> getNotifications(
            Integer userId, int page, int size) {
        return notificationRepository
                .findByRecipientIdOrderByCreatedAtDesc(
                        userId, PageRequest.of(page, size))
                .map(NotificationResponse::fromEntity);
    }

    // GET UNREAD NOTIFICATIONS
    @Override
    public List<NotificationResponse> getUnreadNotifications(
            Integer userId) {
        return notificationRepository
                .findByRecipientIdAndIsReadFalseOrderByCreatedAtDesc(
                        userId)
                .stream()
                .map(NotificationResponse::fromEntity)
                .collect(Collectors.toList());
    }

    // GET UNREAD COUNT
    /*
     * MOST CALLED ENDPOINT in the entire platform.
     * Angular polls this every 30 seconds for badge updates.
     * Must return in <10ms.
     * Has composite DB index: (recipient_id, is_read) → instant.
     */
    @Override
    public long getUnreadCount(Integer userId) {
        return notificationRepository
                .countByRecipientIdAndIsReadFalse(userId);
    }

    // MARK AS READ
    @Override
    @Transactional
    public void markAsRead(Integer notificationId,
                           Integer userId) {
        /*
         * Security check: user can only mark their OWN
         * notifications as read.
         * Without this check: any user could mark anyone's
         * notifications as read (information leakage).
         */
        if (!notificationRepository
                .existsByNotificationIdAndRecipientId(
                        notificationId, userId)) {
            throw new CustomException(
                    "Notification not found or not yours",
                    HttpStatus.NOT_FOUND);
        }

        notificationRepository.markAsRead(notificationId);
        log.debug("Notification {} marked as read by user {}",
                notificationId, userId);
    }

    // MARK ALL AS READ
    @Override
    @Transactional
    public void markAllAsRead(Integer userId) {
        notificationRepository.markAllReadByRecipientId(userId);
        log.info("All notifications marked as read: userId={}",
                userId);
    }

    // DELETE NOTIFICATION
    @Override
    @Transactional
    public void deleteNotification(Integer notificationId,
                                   Integer userId) {
        if (!notificationRepository
                .existsByNotificationIdAndRecipientId(
                        notificationId, userId)) {
            throw new CustomException(
                    "Notification not found or not yours",
                    HttpStatus.NOT_FOUND);
        }

        notificationRepository.deleteByNotificationId(
                notificationId);
        log.debug("Notification {} deleted by user {}",
                notificationId, userId);
    }

    // DELETE READ NOTIFICATIONS
    @Override
    @Transactional
    public void deleteReadNotifications(Integer userId) {
        notificationRepository.deleteReadByRecipientId(userId);
        log.info("🗑️ Read notifications cleared: userId={}", userId);
    }

    // SEND BULK NOTIFICATION (ADMIN)
    @Override
    @Transactional
    public void sendBulkNotification(
            SendBulkNotificationRequest request,
            List<Integer> allUserIds) {

        /*
         * Determine target users:
         * If recipientIds provided → notify those specific users.
         * If targetRole provided → notify users of that role.
         *   (allUserIds passed in from controller, filtered by role)
         * If neither → notify ALL users (system broadcast).
         */
        List<Integer> targetIds;

        if (request.getRecipientIds() != null &&
                !request.getRecipientIds().isEmpty()) {
            targetIds = request.getRecipientIds();
        } else {
            targetIds = allUserIds;
        }

        if (targetIds == null || targetIds.isEmpty()) {
            log.warn("No recipients for bulk notification");
            return;
        }

        int created = 0;
        for (Integer recipientId : targetIds) {
            try {
                Notification notification = Notification.builder()
                        .recipientId(recipientId)
                        .actorId(null) // system notification
                        .type(Notification.NotificationType.SYSTEM)
                        .title(request.getTitle())
                        .message(request.getMessage())
                        .relatedId(null)
                        .relatedType(null)
                        .isRead(false)
                        .build();

                notificationRepository.save(notification);
                created++;
            } catch (Exception e) {
                log.error("Failed to create notification " +
                                "for user {}: {}",
                        recipientId, e.getMessage());
            }
        }

        log.info("Bulk notification sent: {}/{} recipients",
                created, targetIds.size());
    }

    // RABBITMQ: HANDLE COMMENT EVENTS

    @Override
    @RabbitListener(queues = RabbitMQConfig.COMMENT_NOTIFICATION_QUEUE)
    @Transactional
    public void handleCommentEvent(Map<String, Object> payload) {
        try {
            log.info("Received comment event: {}",
                    payload.get("commentId"));

            Integer commentId = (Integer) payload.get("commentId");
            Integer postId    = (Integer) payload.get("postId");
            Integer actorId   = (Integer) payload.get("authorId");
            Boolean isReply   = (Boolean) payload.get("isReply");
            String  content   = (String)  payload.get("content");

            if (Boolean.TRUE.equals(isReply)) {
                /*
                 * Reply to a comment.
                 * Notify the PARENT COMMENT AUTHOR.
                 * parentCommentAuthorId included in event.
                 */
                Integer parentAuthorId =
                        (Integer) payload.get("parentCommentAuthorId");

                if (parentAuthorId != null) {
                    String title = "Someone replied to your comment";
                    String message = truncate(content, 150);

                    createNotification(
                            parentAuthorId, actorId,
                            Notification.NotificationType.COMMENT_REPLY,
                            title, message,
                            commentId, "COMMENT");
                }

            } else {
                /*
                 * New top-level comment.
                 * Notify the POST AUTHOR.
                 * postAuthorId included in event from comment-service.
                 */
                Integer postAuthorId =
                        (Integer) payload.get("postAuthorId");

                if (postAuthorId != null) {
                    String title = "New comment on your post";
                    String message = truncate(content, 150);

                    createNotification(
                            postAuthorId, actorId,
                            Notification.NotificationType.NEW_COMMENT,
                            title, message,
                            postId, "POST");
                }
            }

        } catch (Exception e) {
            log.error("Error handling comment event: {}",
                    e.getMessage(), e);
            // Don't rethrow — prevents message from being requeued
            // in an infinite loop on persistent errors
        }
    }

    // RABBITMQ: HANDLE POST PUBLISHED EVENTS

    @Override
    @RabbitListener(queues =
            RabbitMQConfig.POST_PUBLISHED_NOTIFICATION_QUEUE)
    @Transactional
    public void handlePostPublishedEvent(
            Map<String, Object> payload) {
        try {
            log.info("Received post.published event: postId={}",
                    payload.get("postId"));

            Integer postId   = (Integer) payload.get("postId");
            Integer authorId = (Integer) payload.get("authorId");
            String  title    = (String)  payload.get("title");
            String  slug     = (String)  payload.get("slug");

            /*
             * TODO: In production, fetch follower IDs:
             * List<Integer> followerIds =
             *     authServiceClient.getFollowers(authorId);
             *
             * For now: log and return.
             * Followers feature is a future enhancement.
             */
            log.info("New post published: '{}' by authorId={}. " +
                            "Follower notifications would be sent here.",
                    title, authorId);

        } catch (Exception e) {
            log.error("Error handling post published event: {}",
                    e.getMessage(), e);
        }
    }

    // SCHEDULED CLEANUP

    @Override
    @Scheduled(cron = "0 0 2 * * *")
    @Transactional
    public void cleanupOldNotifications() {
        LocalDateTime cutoff = LocalDateTime.now()
                .minusDays(retentionDays);

        log.info("Running notification cleanup. " +
                "Deleting before: {}", cutoff);

        notificationRepository.deleteOlderThan(cutoff);

        log.info("Notification cleanup complete.");
    }

    // PRIVATE HELPERS

    private String truncate(String text, int maxLength) {
        if (text == null) return "";
        if (text.length() <= maxLength) return text;
        return text.substring(0, maxLength) + "...";
    }
}