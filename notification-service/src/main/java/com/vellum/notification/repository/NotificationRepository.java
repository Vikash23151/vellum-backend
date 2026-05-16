package com.vellum.notification.repository;

import com.vellum.notification.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface NotificationRepository
        extends JpaRepository<Notification, Integer> {

    /*
     * Get all notifications for a user, newest first.
     * Paginated: user doesn't need to see 500 old notifications.
     * page=0, size=20 → shows last 20 notifications.
     */
    Page<Notification> findByRecipientIdOrderByCreatedAtDesc(
            Integer recipientId, Pageable pageable);

    /*
     * Get unread notifications for a user.
     * Used for: showing unread notifications in dropdown.
     */
    List<Notification> findByRecipientIdAndIsReadFalseOrderByCreatedAtDesc(
            Integer recipientId);

    /*
     * Count unread notifications.
     * Called by Angular every 30 seconds for badge update.
     * MUST be fast — has composite index (recipient_id, is_read).
     * This query returns in <1ms with the index.
     */
    long countByRecipientIdAndIsReadFalse(Integer recipientId);

    /*
     * Mark ALL notifications as read for a user.
     * Triggered when user clicks "Mark all as read".
     * @Modifying + @Query = efficient bulk UPDATE.
     * Alternative: load all, set isRead=true, saveAll = N queries!
     * This = 1 query regardless of notification count.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Transactional
    @Query("UPDATE Notification n SET n.isRead = true " +
            "WHERE n.recipientId = :recipientId " +
            "AND n.isRead = false")
    void markAllReadByRecipientId(@Param("recipientId")
                                  Integer recipientId);

    /*
     * Mark a single notification as read.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Transactional
    @Query("UPDATE Notification n SET n.isRead = true " +
            "WHERE n.notificationId = :notificationId")
    void markAsRead(@Param("notificationId") Integer notificationId);

    /*
     * Delete all read notifications for a user.
     * "Clear all" button in notification center.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Transactional
    @Query("DELETE FROM Notification n " +
            "WHERE n.recipientId = :recipientId " +
            "AND n.isRead = true")
    void deleteReadByRecipientId(@Param("recipientId")
                                 Integer recipientId);

    /*
     * Delete a specific notification.
     * User dismisses one notification.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Transactional
    void deleteByNotificationId(Integer notificationId);

    /*
     * Retention cleanup: delete notifications older than N days.
     * Called by scheduled task daily at midnight.
     * Prevents the table from growing indefinitely.
     *
     * Example: retentionDate = now - 90 days
     * Deletes all notifications created before that date.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Transactional
    @Query("DELETE FROM Notification n " +
            "WHERE n.createdAt < :retentionDate")
    void deleteOlderThan(@Param("retentionDate")
                         LocalDateTime retentionDate);

    /*
     * Count total notifications for a user.
     * Used to enforce max-per-user limit.
     * When exceeded: delete oldest before inserting new.
     */
    long countByRecipientId(Integer recipientId);

    /*
     * Find oldest notifications for a user.
     * Used when max-per-user limit is exceeded.
     * Delete oldest to make room for newest.
     */
    @Query("SELECT n FROM Notification n " +
            "WHERE n.recipientId = :recipientId " +
            "ORDER BY n.createdAt ASC")
    List<Notification> findOldestByRecipientId(
            @Param("recipientId") Integer recipientId,
            Pageable pageable);

    /*
     * Find notifications by type (for admin analytics).
     */
    List<Notification> findByTypeOrderByCreatedAtDesc(
            Notification.NotificationType type, Pageable pageable);

    /*
     * Check if a notification belongs to a user.
     * Security check before marking as read or deleting.
     */
    boolean existsByNotificationIdAndRecipientId(
            Integer notificationId, Integer recipientId);
}