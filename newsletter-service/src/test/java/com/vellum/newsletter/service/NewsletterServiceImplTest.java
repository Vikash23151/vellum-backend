package com.vellum.newsletter.service;

import com.vellum.newsletter.dto.*;
import com.vellum.newsletter.entity.Subscriber;
import com.vellum.newsletter.exception.CustomException;
import com.vellum.newsletter.repository.SubscriberRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("NewsletterServiceImpl Unit Tests")
class NewsletterServiceImplTest {

    @Mock private SubscriberRepository subscriberRepository;
    @Mock private EmailService         emailService;

    @InjectMocks
    private NewsletterServiceImpl newsletterService;

    private Subscriber activeSubscriber;
    private Subscriber pendingSubscriber;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(newsletterService,
                "tokenExpiryHours", 24);
        ReflectionTestUtils.setField(newsletterService,
                "batchSize", 50);
        ReflectionTestUtils.setField(newsletterService,
                "batchDelayMs", 0L);

        activeSubscriber = Subscriber.builder()
                .subscriberId(1)
                .email("active@test.com")
                .fullName("Active User")
                .status(Subscriber.SubscriberStatus.ACTIVE)
                .token("active-token-uuid-here")
                .tokenCreatedAt(LocalDateTime.now())
                .preferences("java")
                .subscribedAt(LocalDateTime.now())
                .confirmedAt(LocalDateTime.now())
                .build();

        pendingSubscriber = Subscriber.builder()
                .subscriberId(2)
                .email("pending@test.com")
                .fullName("Pending User")
                .status(Subscriber.SubscriberStatus.PENDING)
                .token("pending-token-uuid-here")
                .tokenCreatedAt(LocalDateTime.now())
                .preferences("")
                .subscribedAt(LocalDateTime.now())
                .build();
    }

    // subscribe() tests

    @Test
    @DisplayName("subscribe: new subscriber created as PENDING")
    void subscribe_newEmail_createsPending() {
        SubscribeRequest request = new SubscribeRequest();
        request.setEmail("new@test.com");
        request.setFullName("New User");

        when(subscriberRepository.findByEmail("new@test.com"))
                .thenReturn(Optional.empty());
        when(subscriberRepository.save(any(Subscriber.class)))
                .thenAnswer(inv -> {
                    Subscriber s = inv.getArgument(0);
                    s.setSubscriberId(3);
                    return s;
                });

        SubscriberResponse result = newsletterService.subscribe(request);

        assertThat(result.getStatus()).isEqualTo("PENDING");
        verify(emailService, times(1))
                .sendConfirmationEmail(
                        eq("new@test.com"), any(), any());
    }

    @Test
    @DisplayName("subscribe: throws CONFLICT for already ACTIVE subscriber")
    void subscribe_alreadyActive_throwsConflict() {
        SubscribeRequest request = new SubscribeRequest();
        request.setEmail("active@test.com");

        when(subscriberRepository.findByEmail("active@test.com"))
                .thenReturn(Optional.of(activeSubscriber));

        assertThatThrownBy(() -> newsletterService.subscribe(request))
                .isInstanceOf(CustomException.class)
                .satisfies(ex -> assertThat(
                        ((CustomException) ex).getStatus())
                        .isEqualTo(HttpStatus.CONFLICT));

        verify(emailService, never()).sendConfirmationEmail(
                any(), any(), any());
    }

    @Test
    @DisplayName("subscribe: resends confirmation for PENDING subscriber")
    void subscribe_pending_ressendsConfirmation() {
        SubscribeRequest request = new SubscribeRequest();
        request.setEmail("pending@test.com");

        when(subscriberRepository.findByEmail("pending@test.com"))
                .thenReturn(Optional.of(pendingSubscriber));
        when(subscriberRepository.save(any(Subscriber.class)))
                .thenReturn(pendingSubscriber);

        newsletterService.subscribe(request);

        verify(emailService, times(1))
                .sendConfirmationEmail(
                        eq("pending@test.com"), any(), any());
    }

    // confirmSubscription() tests

    @Test
    @DisplayName("confirmSubscription: activates PENDING subscriber")
    void confirmSubscription_validToken_activates() {
        when(subscriberRepository.findByToken("pending-token-uuid-here"))
                .thenReturn(Optional.of(pendingSubscriber));
        when(subscriberRepository.save(any(Subscriber.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        SubscriberResponse result = newsletterService
                .confirmSubscription("pending-token-uuid-here");

        assertThat(result.getStatus()).isEqualTo("ACTIVE");
        verify(emailService, times(1))
                .sendWelcomeEmail(
                        eq("pending@test.com"), any(), any());
    }

    @Test
    @DisplayName("confirmSubscription: throws NOT_FOUND for invalid token")
    void confirmSubscription_invalidToken_throwsNotFound() {
        when(subscriberRepository.findByToken("bad-token"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                newsletterService.confirmSubscription("bad-token"))
                .isInstanceOf(CustomException.class)
                .satisfies(ex -> assertThat(
                        ((CustomException) ex).getStatus())
                        .isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    @DisplayName("confirmSubscription: throws GONE for expired token")
    void confirmSubscription_expiredToken_throwsGone() {
        // Set tokenCreatedAt to 25 hours ago (beyond 24h expiry)
        pendingSubscriber.setTokenCreatedAt(
                LocalDateTime.now().minusHours(25));

        when(subscriberRepository.findByToken("pending-token-uuid-here"))
                .thenReturn(Optional.of(pendingSubscriber));

        assertThatThrownBy(() ->
                newsletterService.confirmSubscription(
                        "pending-token-uuid-here"))
                .isInstanceOf(CustomException.class)
                .satisfies(ex -> assertThat(
                        ((CustomException) ex).getStatus())
                        .isEqualTo(HttpStatus.GONE));

        verify(emailService, never()).sendWelcomeEmail(any(), any(), any());
    }

    @Test
    @DisplayName("confirmSubscription: throws BAD_REQUEST if already ACTIVE")
    void confirmSubscription_alreadyActive_throwsBadRequest() {
        when(subscriberRepository.findByToken("active-token-uuid-here"))
                .thenReturn(Optional.of(activeSubscriber));

        assertThatThrownBy(() ->
                newsletterService.confirmSubscription(
                        "active-token-uuid-here"))
                .isInstanceOf(CustomException.class)
                .satisfies(ex -> assertThat(
                        ((CustomException) ex).getStatus())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
    }

    // unsubscribe() tests

    @Test
    @DisplayName("unsubscribe: active subscriber is unsubscribed")
    void unsubscribe_active_setsUnsubscribed() {
        when(subscriberRepository.findByToken("active-token-uuid-here"))
                .thenReturn(Optional.of(activeSubscriber));
        when(subscriberRepository.save(any(Subscriber.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        assertThatCode(() ->
                newsletterService.unsubscribe("active-token-uuid-here"))
                .doesNotThrowAnyException();

        verify(subscriberRepository, times(1))
                .save(argThat(s ->
                        s.getStatus() ==
                                Subscriber.SubscriberStatus.UNSUBSCRIBED));
    }

    @Test
    @DisplayName("unsubscribe: idempotent for already-unsubscribed")
    void unsubscribe_alreadyUnsubscribed_noError() {
        Subscriber unsubbed = Subscriber.builder()
                .subscriberId(3)
                .email("unsub@test.com")
                .status(Subscriber.SubscriberStatus.UNSUBSCRIBED)
                .token("unsub-token")
                .tokenCreatedAt(LocalDateTime.now())
                .build();

        when(subscriberRepository.findByToken("unsub-token"))
                .thenReturn(Optional.of(unsubbed));

        // Should NOT throw — idempotent operation
        assertThatCode(() ->
                newsletterService.unsubscribe("unsub-token"))
                .doesNotThrowAnyException();

        // Should NOT save again (already in correct state)
        verify(subscriberRepository, never()).save(any());
    }

    // getStats() tests

    @Test
    @DisplayName("getStats: returns correct subscriber counts")
    void getStats_returnsCorrectCounts() {
        when(subscriberRepository.count()).thenReturn(100L);
        when(subscriberRepository.countByStatus(
                Subscriber.SubscriberStatus.ACTIVE)).thenReturn(70L);
        when(subscriberRepository.countByStatus(
                Subscriber.SubscriberStatus.PENDING)).thenReturn(20L);
        when(subscriberRepository.countByStatus(
                Subscriber.SubscriberStatus.UNSUBSCRIBED)).thenReturn(10L);

        Map<String, Long> stats = newsletterService.getStats();

        assertThat(stats.get("total")).isEqualTo(100L);
        assertThat(stats.get("active")).isEqualTo(70L);
        assertThat(stats.get("pending")).isEqualTo(20L);
        assertThat(stats.get("unsubscribed")).isEqualTo(10L);
    }
}