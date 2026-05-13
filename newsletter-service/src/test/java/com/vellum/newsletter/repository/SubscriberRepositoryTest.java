package com.vellum.newsletter.repository;

import com.vellum.newsletter.entity.Subscriber;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@DisplayName("SubscriberRepository Tests")
class SubscriberRepositoryTest {

    @Autowired
    private SubscriberRepository subscriberRepository;

    private Subscriber activeSubscriber;
    private Subscriber pendingSubscriber;
    private Subscriber unsubscribedSubscriber;

    @BeforeEach
    void setUp() {
        activeSubscriber = subscriberRepository.save(
                Subscriber.builder()
                        .email("active@test.com")
                        .fullName("Active User")
                        .status(Subscriber.SubscriberStatus.ACTIVE)
                        .token(UUID.randomUUID().toString())
                        .tokenCreatedAt(LocalDateTime.now())
                        .preferences("java,spring-boot")
                        .subscribedAt(LocalDateTime.now())
                        .confirmedAt(LocalDateTime.now())
                        .build());

        pendingSubscriber = subscriberRepository.save(
                Subscriber.builder()
                        .email("pending@test.com")
                        .fullName("Pending User")
                        .status(Subscriber.SubscriberStatus.PENDING)
                        .token(UUID.randomUUID().toString())
                        .tokenCreatedAt(LocalDateTime.now())
                        .preferences("")
                        .subscribedAt(LocalDateTime.now())
                        .build());

        unsubscribedSubscriber = subscriberRepository.save(
                Subscriber.builder()
                        .email("unsub@test.com")
                        .fullName("Unsubscribed User")
                        .status(Subscriber.SubscriberStatus.UNSUBSCRIBED)
                        .token(UUID.randomUUID().toString())
                        .tokenCreatedAt(LocalDateTime.now())
                        .preferences("")
                        .subscribedAt(LocalDateTime.now())
                        .build());
    }

    @Test
    @DisplayName("findByEmail: returns subscriber when email exists")
    void findByEmail_exists_returnsSubscriber() {
        Optional<Subscriber> result =
                subscriberRepository.findByEmail("active@test.com");

        assertThat(result).isPresent();
        assertThat(result.get().getStatus())
                .isEqualTo(Subscriber.SubscriberStatus.ACTIVE);
    }

    @Test
    @DisplayName("findByToken: returns subscriber for valid token")
    void findByToken_valid_returnsSubscriber() {
        Optional<Subscriber> result =
                subscriberRepository.findByToken(
                        pendingSubscriber.getToken());

        assertThat(result).isPresent();
        assertThat(result.get().getEmail())
                .isEqualTo("pending@test.com");
    }

    @Test
    @DisplayName("findByStatus(ACTIVE): returns only active subscribers")
    void findByStatus_active_returnsActiveOnly() {
        List<Subscriber> active = subscriberRepository.findByStatus(
                Subscriber.SubscriberStatus.ACTIVE);

        assertThat(active).hasSize(1);
        assertThat(active.get(0).getEmail())
                .isEqualTo("active@test.com");
    }

    @Test
    @DisplayName("findActiveByPreference: filters by preference tag")
    void findActiveByPreference_returnsMatching() {
        List<Subscriber> result =
                subscriberRepository.findActiveByPreference("java");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getEmail())
                .isEqualTo("active@test.com");
    }

    @Test
    @DisplayName("findActiveByPreference: returns empty for unknown preference")
    void findActiveByPreference_noMatch_returnsEmpty() {
        List<Subscriber> result =
                subscriberRepository.findActiveByPreference("python");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("countByStatus: returns correct counts")
    void countByStatus_returnsCorrectCounts() {
        assertThat(subscriberRepository.countByStatus(
                Subscriber.SubscriberStatus.ACTIVE)).isEqualTo(1);
        assertThat(subscriberRepository.countByStatus(
                Subscriber.SubscriberStatus.PENDING)).isEqualTo(1);
        assertThat(subscriberRepository.countByStatus(
                Subscriber.SubscriberStatus.UNSUBSCRIBED)).isEqualTo(1);
    }

    @Test
    @DisplayName("updateStatus: changes status correctly")
    void updateStatus_changesStatus() {
        subscriberRepository.updateStatus(
                pendingSubscriber.getSubscriberId(),
                Subscriber.SubscriberStatus.ACTIVE);

        Subscriber updated = subscriberRepository
                .findById(pendingSubscriber.getSubscriberId()).get();
        assertThat(updated.getStatus())
                .isEqualTo(Subscriber.SubscriberStatus.ACTIVE);
    }

    @Test
    @DisplayName("existsByEmail: returns true for existing email")
    void existsByEmail_exists_returnsTrue() {
        assertThat(subscriberRepository
                .existsByEmail("active@test.com")).isTrue();
        assertThat(subscriberRepository
                .existsByEmail("nobody@test.com")).isFalse();
    }
}