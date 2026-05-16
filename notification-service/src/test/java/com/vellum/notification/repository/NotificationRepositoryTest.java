package com.vellum.notification.repository;

import com.vellum.notification.entity.Notification;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@DisplayName("NotificationRepository Tests")
class NotificationRepositoryTest {

    @Autowired
    private NotificationRepository notificationRepository;

    private Notification unreadNotif1;
    private Notification unreadNotif2;
    private Notification readNotif;

    @BeforeEach
    void setUp() {
        unreadNotif1 = notificationRepository.save(
                Notification.builder()
                        .recipientId(1)
                        .actorId(2)
                        .type(Notification.NotificationType.NEW_COMMENT)
                        .title("New comment on your post")
                        .message("Great article!")
                        .relatedId(10)
                        .relatedType("POST")
                        .isRead(false)
                        .build());

        unreadNotif2 = notificationRepository.save(
                Notification.builder()
                        .recipientId(1)
                        .actorId(3)
                        .type(Notification.NotificationType.LIKE)
                        .title("Someone liked your post")
                        .message("")
                        .relatedId(10)
                        .relatedType("POST")
                        .isRead(false)
                        .build());

        readNotif = notificationRepository.save(
                Notification.builder()
                        .recipientId(1)
                        .actorId(4)
                        .type(Notification.NotificationType.COMMENT_REPLY)
                        .title("Someone replied to your comment")
                        .message("I agree!")
                        .relatedId(50)
                        .relatedType("COMMENT")
                        .isRead(true)
                        .build());

        // Notification for different user
        notificationRepository.save(
                Notification.builder()
                        .recipientId(2)
                        .actorId(1)
                        .type(Notification.NotificationType.NEW_COMMENT)
                        .title("New comment")
                        .message("Hello")
                        .relatedId(20)
                        .relatedType("POST")
                        .isRead(false)
                        .build());
    }

    @Test
    @DisplayName("findByRecipientId: returns paginated notifications")
    void findByRecipientId_returnsPaginated() {
        Page<Notification> page =
                notificationRepository
                        .findByRecipientIdOrderByCreatedAtDesc(
                                1, PageRequest.of(0, 10));

        // user 1 has 3 notifications (2 unread + 1 read)
        assertThat(page.getContent()).hasSize(3);
        assertThat(page.getContent()).allMatch(
                n -> n.getRecipientId().equals(1));
    }

    @Test
    @DisplayName("findUnread: returns only unread for recipient")
    void findUnread_returnsOnlyUnread() {
        List<Notification> unread =
                notificationRepository
                        .findByRecipientIdAndIsReadFalseOrderByCreatedAtDesc(1);

        assertThat(unread).hasSize(2);
        assertThat(unread).allMatch(n -> !n.isRead());
    }

    @Test
    @DisplayName("countUnread: returns correct unread count")
    void countUnread_returnsCorrectCount() {
        long count = notificationRepository
                .countByRecipientIdAndIsReadFalse(1);

        assertThat(count).isEqualTo(2);
    }

    @Test
    @DisplayName("countUnread: returns 0 for user with no unread")
    void countUnread_noUnread_returnsZero() {
        // Mark all as read for user 1
        notificationRepository.markAllReadByRecipientId(1);

        long count = notificationRepository
                .countByRecipientIdAndIsReadFalse(1);

        assertThat(count).isEqualTo(0);
    }

    @Test
    @DisplayName("markAsRead: sets isRead to true for single notification")
    void markAsRead_setsReadTrue() {
        notificationRepository.markAsRead(
                unreadNotif1.getNotificationId());

        Notification updated = notificationRepository
                .findById(unreadNotif1.getNotificationId()).get();

        assertThat(updated.isRead()).isTrue();
    }

    @Test
    @DisplayName("markAllRead: marks all unread as read for recipient")
    void markAllRead_marksAllAsRead() {
        notificationRepository.markAllReadByRecipientId(1);

        long remaining = notificationRepository
                .countByRecipientIdAndIsReadFalse(1);

        assertThat(remaining).isEqualTo(0);
    }

    @Test
    @DisplayName("deleteRead: deletes only read notifications")
    void deleteRead_deletesOnlyRead() {
        notificationRepository.deleteReadByRecipientId(1);

        Page<Notification> remaining =
                notificationRepository
                        .findByRecipientIdOrderByCreatedAtDesc(
                                1, PageRequest.of(0, 10));

        // readNotif deleted, 2 unread remain
        assertThat(remaining.getContent()).hasSize(2);
        assertThat(remaining.getContent())
                .allMatch(n -> !n.isRead());
    }

    @Test
    @DisplayName("existsByNotificationIdAndRecipientId: true for owner")
    void existsByNotificationIdAndRecipientId_owner_true() {
        assertThat(notificationRepository
                .existsByNotificationIdAndRecipientId(
                        unreadNotif1.getNotificationId(), 1))
                .isTrue();
    }

    @Test
    @DisplayName("existsByNotificationIdAndRecipientId: false for non-owner")
    void existsByNotificationIdAndRecipientId_nonOwner_false() {
        // notification belongs to user 1, checking with user 99
        assertThat(notificationRepository
                .existsByNotificationIdAndRecipientId(
                        unreadNotif1.getNotificationId(), 99))
                .isFalse();
    }

    @Test
    @DisplayName("deleteOlderThan: deletes old notifications")
    void deleteOlderThan_deletesOldRecords() {
        // All notifications are recent (created in @BeforeEach)
        // Set cutoff to future: should delete everything
        LocalDateTime futureCutoff = LocalDateTime.now().plusHours(1);

        notificationRepository.deleteOlderThan(futureCutoff);

        long remaining = notificationRepository.count();
        assertThat(remaining).isEqualTo(0);
    }

    @Test
    @DisplayName("deleteOlderThan: keeps recent notifications")
    void deleteOlderThan_keepsRecentNotifications() {
        // Set cutoff to yesterday: should keep today's notifications
        LocalDateTime yesterdayCutoff = LocalDateTime.now().minusDays(1);

        notificationRepository.deleteOlderThan(yesterdayCutoff);

        // All 4 notifications created just now → all kept
        long remaining = notificationRepository.count();
        assertThat(remaining).isEqualTo(4);
    }

    @Test
    @DisplayName("findOldestByRecipientId: returns oldest first")
    void findOldestByRecipientId_returnsOldestFirst() {
        List<Notification> oldest = notificationRepository
                .findOldestByRecipientId(1, PageRequest.of(0, 1));

        assertThat(oldest).hasSize(1);
        // First saved = oldest
        assertThat(oldest.get(0).getNotificationId())
                .isEqualTo(unreadNotif1.getNotificationId());
    }
}