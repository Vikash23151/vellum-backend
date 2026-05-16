package com.vellum.notification.service;

import com.vellum.notification.dto.*;
import com.vellum.notification.entity.Notification;
import com.vellum.notification.exception.CustomException;
import com.vellum.notification.repository.NotificationRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationServiceImpl Unit Tests")
class NotificationServiceImplTest {

    @Mock
    private NotificationRepository notificationRepository;

    @InjectMocks
    private NotificationServiceImpl notificationService;

    private Notification testNotification;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(
                notificationService, "retentionDays", 90);
        ReflectionTestUtils.setField(
                notificationService, "maxPerUser", 100);

        testNotification = Notification.builder()
                .notificationId(1)
                .recipientId(10)
                .actorId(20)
                .type(Notification.NotificationType.NEW_COMMENT)
                .title("New comment on your post")
                .message("Great article!")
                .relatedId(5)
                .relatedType("POST")
                .isRead(false)
                .createdAt(LocalDateTime.now())
                .build();
    }

    // createNotification tests

    @Test
    @DisplayName("createNotification: creates and saves notification")
    void createNotification_success() {
        when(notificationRepository.countByRecipientId(10))
                .thenReturn(50L); // under limit
        when(notificationRepository.save(any(Notification.class)))
                .thenReturn(testNotification);

        NotificationResponse result =
                notificationService.createNotification(
                        10, 20,
                        Notification.NotificationType.NEW_COMMENT,
                        "New comment on your post",
                        "Great article!",
                        5, "POST");

        assertThat(result).isNotNull();
        assertThat(result.getType())
                .isEqualTo("NEW_COMMENT");
        verify(notificationRepository, times(1))
                .save(any(Notification.class));
    }

    @Test
    @DisplayName("createNotification: skips self-notification")
    void createNotification_selfNotification_returnsNull() {
        /*
         * actorId == recipientId → same user → skip notification.
         * Author commenting on own post should not get a notification.
         */
        NotificationResponse result =
                notificationService.createNotification(
                        10, 10, // same user!
                        Notification.NotificationType.NEW_COMMENT,
                        "New comment", "Content",
                        5, "POST");

        assertThat(result).isNull();
        verify(notificationRepository, never())
                .save(any(Notification.class));
    }

    @Test
    @DisplayName("createNotification: enforces max-per-user limit")
    void createNotification_atLimit_deletesOldest() {
        when(notificationRepository.countByRecipientId(10))
                .thenReturn(100L); // at limit!

        Notification oldest = Notification.builder()
                .notificationId(999)
                .recipientId(10)
                .type(Notification.NotificationType.LIKE)
                .title("Old notification")
                .isRead(true)
                .build();

        when(notificationRepository.findOldestByRecipientId(
                eq(10), any(PageRequest.class)))
                .thenReturn(List.of(oldest));
        when(notificationRepository.save(any(Notification.class)))
                .thenReturn(testNotification);

        notificationService.createNotification(
                10, 20,
                Notification.NotificationType.NEW_COMMENT,
                "New comment", "Content",
                5, "POST");

        // Oldest should be deleted before new one is saved
        verify(notificationRepository, times(1)).delete(oldest);
        verify(notificationRepository, times(1))
                .save(any(Notification.class));
    }

    // getUnreadCount tests

    @Test
    @DisplayName("getUnreadCount: returns count from repository")
    void getUnreadCount_returnsCorrectCount() {
        when(notificationRepository
                .countByRecipientIdAndIsReadFalse(10))
                .thenReturn(7L);

        long count = notificationService.getUnreadCount(10);

        assertThat(count).isEqualTo(7L);
    }

    // markAsRead tests

    @Test
    @DisplayName("markAsRead: marks notification when user owns it")
    void markAsRead_owner_success() {
        when(notificationRepository
                .existsByNotificationIdAndRecipientId(1, 10))
                .thenReturn(true);

        assertThatCode(() ->
                notificationService.markAsRead(1, 10))
                .doesNotThrowAnyException();

        verify(notificationRepository, times(1)).markAsRead(1);
    }

    @Test
    @DisplayName("markAsRead: throws 404 when not owner")
    void markAsRead_nonOwner_throwsNotFound() {
        when(notificationRepository
                .existsByNotificationIdAndRecipientId(1, 99))
                .thenReturn(false);

        assertThatThrownBy(() ->
                notificationService.markAsRead(1, 99))
                .isInstanceOf(CustomException.class)
                .satisfies(ex -> assertThat(
                        ((CustomException) ex).getStatus())
                        .isEqualTo(HttpStatus.NOT_FOUND));

        verify(notificationRepository, never()).markAsRead(any());
    }

    // markAllAsRead tests

    @Test
    @DisplayName("markAllAsRead: calls repository bulk update")
    void markAllAsRead_callsRepository() {
        notificationService.markAllAsRead(10);

        verify(notificationRepository, times(1))
                .markAllReadByRecipientId(10);
    }

    // deleteNotification tests

    @Test
    @DisplayName("deleteNotification: owner can delete own notification")
    void deleteNotification_owner_deletes() {
        when(notificationRepository
                .existsByNotificationIdAndRecipientId(1, 10))
                .thenReturn(true);

        assertThatCode(() ->
                notificationService.deleteNotification(1, 10))
                .doesNotThrowAnyException();

        verify(notificationRepository, times(1))
                .deleteByNotificationId(1);
    }

    @Test
    @DisplayName("deleteNotification: throws 404 for non-owner")
    void deleteNotification_nonOwner_throwsNotFound() {
        when(notificationRepository
                .existsByNotificationIdAndRecipientId(1, 99))
                .thenReturn(false);

        assertThatThrownBy(() ->
                notificationService.deleteNotification(1, 99))
                .isInstanceOf(CustomException.class)
                .satisfies(ex -> assertThat(
                        ((CustomException) ex).getStatus())
                        .isEqualTo(HttpStatus.NOT_FOUND));
    }

    // handleCommentEvent tests

    @Test
    @DisplayName("handleCommentEvent: creates NEW_COMMENT notification")
    void handleCommentEvent_newComment_createsNotification() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("commentId",       50);
        payload.put("postId",          10);
        payload.put("authorId",        20); // commenter
        payload.put("postAuthorId",    10); // post owner
        payload.put("isReply",         false);
        payload.put("content",         "Great article!");

        when(notificationRepository.countByRecipientId(anyInt()))
                .thenReturn(0L);
        when(notificationRepository.save(any(Notification.class)))
                .thenReturn(testNotification);

        notificationService.handleCommentEvent(payload);

        // Should save a notification for the post author
        verify(notificationRepository, atLeastOnce())
                .save(any(Notification.class));
    }

    @Test
    @DisplayName("handleCommentEvent: creates COMMENT_REPLY notification")
    void handleCommentEvent_reply_createsReplyNotification() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("commentId",             60);
        payload.put("postId",                10);
        payload.put("authorId",              30);   // replier
        payload.put("parentCommentAuthorId", 20);   // original commenter
        payload.put("isReply",               true);
        payload.put("content",               "I agree with you!");

        Notification replyNotif = Notification.builder()
                .notificationId(2)
                .recipientId(20)
                .actorId(30)
                .type(Notification.NotificationType.COMMENT_REPLY)
                .title("Someone replied to your comment")
                .isRead(false)
                .createdAt(LocalDateTime.now())
                .build();

        when(notificationRepository.countByRecipientId(20))
                .thenReturn(0L);
        when(notificationRepository.save(any(Notification.class)))
                .thenReturn(replyNotif);

        notificationService.handleCommentEvent(payload);

        verify(notificationRepository, atLeastOnce())
                .save(argThat(n ->
                        n.getType() ==
                                Notification.NotificationType.COMMENT_REPLY));
    }

    @Test
    @DisplayName("handleCommentEvent: does not crash on bad payload")
    void handleCommentEvent_badPayload_handlesGracefully() {
        Map<String, Object> emptyPayload = new HashMap<>();

        // Should not throw — errors are caught and logged
        assertThatCode(() ->
                notificationService.handleCommentEvent(emptyPayload))
                .doesNotThrowAnyException();
    }

    // sendBulkNotification tests

    @Test
    @DisplayName("sendBulkNotification: creates notification for each user")
    void sendBulkNotification_createsForAllUsers() {
        SendBulkNotificationRequest request =
                new SendBulkNotificationRequest();
        request.setTitle("System maintenance tonight");
        request.setMessage("We will be down from 2AM-3AM.");

        List<Integer> userIds = List.of(1, 2, 3);

        when(notificationRepository.save(any(Notification.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        notificationService.sendBulkNotification(request, userIds);

        // One save per user
        verify(notificationRepository, times(3))
                .save(any(Notification.class));
    }
}