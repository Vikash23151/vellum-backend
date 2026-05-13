package com.vellum.newsletter.service;

import com.vellum.newsletter.config.RabbitMQConfig;
import com.vellum.newsletter.dto.*;
import com.vellum.newsletter.entity.Subscriber;
import com.vellum.newsletter.exception.CustomException;
import com.vellum.newsletter.repository.SubscriberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class NewsletterServiceImpl implements NewsletterService {

    private final SubscriberRepository subscriberRepository;
    private final EmailService         emailService;

    @Value("${app.newsletter.token-expiry-hours:24}")
    private int tokenExpiryHours;

    @Value("${app.newsletter.batch-size:50}")
    private int batchSize;

    @Value("${app.newsletter.batch-delay-ms:1000}")
    private long batchDelayMs;

    // SUBSCRIBE
    @Override
    @Transactional
    public SubscriberResponse subscribe(SubscribeRequest request) {
        log.info("Subscribe request: {}", request.getEmail());

        // Check if already subscribed
        Optional<Subscriber> existing =
                subscriberRepository.findByEmail(request.getEmail());

        if (existing.isPresent()) {
            Subscriber sub = existing.get();

            switch (sub.getStatus()) {
                case ACTIVE:
                    throw new CustomException(
                            "This email is already subscribed and active.",
                            HttpStatus.CONFLICT);

                case PENDING:
                    /*
                     * Already PENDING — resend confirmation.
                     * Generate a fresh token and update tokenCreatedAt.
                     * Handles case: user subscribed but never confirmed.
                     */
                    log.info("Re-sending confirmation for pending: {}",
                            request.getEmail());
                    sub.setToken(generateToken());
                    sub.setTokenCreatedAt(LocalDateTime.now());
                    subscriberRepository.save(sub);

                    emailService.sendConfirmationEmail(
                            sub.getEmail(),
                            sub.getFullName(),
                            sub.getToken());

                    return SubscriberResponse.fromEntity(sub);

                case UNSUBSCRIBED:
                    /*
                     * Previously unsubscribed — allow re-subscribe.
                     * Reset status to PENDING, generate new token.
                     * Must go through double opt-in again.
                     */
                    sub.setStatus(Subscriber.SubscriberStatus.PENDING);
                    sub.setToken(generateToken());
                    sub.setTokenCreatedAt(LocalDateTime.now());
                    sub.setSubscribedAt(LocalDateTime.now());
                    sub.setUnsubscribedAt(null);

                    if (request.getPreferences() != null) {
                        sub.setPreferences(request.getPreferences());
                    }

                    subscriberRepository.save(sub);

                    emailService.sendConfirmationEmail(
                            sub.getEmail(),
                            sub.getFullName(),
                            sub.getToken());

                    return SubscriberResponse.fromEntity(sub);
            }
        }

        // New subscriber
        String token = generateToken();

        Subscriber subscriber = Subscriber.builder()
                .email(request.getEmail())
                .userId(request.getUserId())
                .fullName(request.getFullName())
                .status(Subscriber.SubscriberStatus.PENDING)
                .token(token)
                .tokenCreatedAt(LocalDateTime.now())
                .preferences(request.getPreferences() != null
                        ? request.getPreferences() : "")
                .subscribedAt(LocalDateTime.now())
                .build();

        Subscriber saved = subscriberRepository.save(subscriber);

        // Send confirmation email asynchronously
        emailService.sendConfirmationEmail(
                saved.getEmail(),
                saved.getFullName(),
                saved.getToken());

        log.info("Subscriber created (PENDING): {}",
                saved.getEmail());
        return SubscriberResponse.fromEntity(saved);
    }

    // CONFIRM SUBSCRIPTION
    @Override
    @Transactional
    public SubscriberResponse confirmSubscription(String token) {
        log.info("Confirming subscription with token: {}",
                token.substring(0, 8) + "...");

        Subscriber subscriber = subscriberRepository
                .findByToken(token)
                .orElseThrow(() -> new CustomException(
                        "Invalid confirmation link. Please subscribe again.",
                        HttpStatus.NOT_FOUND));

        // Check if already confirmed
        if (subscriber.getStatus() ==
                Subscriber.SubscriberStatus.ACTIVE) {
            throw new CustomException(
                    "This subscription is already confirmed.",
                    HttpStatus.BAD_REQUEST);
        }

        if (subscriber.getStatus() ==
                Subscriber.SubscriberStatus.UNSUBSCRIBED) {
            throw new CustomException(
                    "This subscription has been cancelled.",
                    HttpStatus.BAD_REQUEST);
        }

        /*
         * Check token expiry.
         * If token is older than 24 hours → expired.
         * User must re-subscribe to get a fresh token.
         */
        long hoursOld = ChronoUnit.HOURS.between(
                subscriber.getTokenCreatedAt(), LocalDateTime.now());

        if (hoursOld >= tokenExpiryHours) {
            throw new CustomException(
                    String.format(
                            "Confirmation link has expired (valid for %d hours). " +
                                    "Please subscribe again to get a new link.",
                            tokenExpiryHours),
                    HttpStatus.GONE);
        }

        // Activate subscription
        subscriber.setStatus(Subscriber.SubscriberStatus.ACTIVE);
        subscriber.setConfirmedAt(LocalDateTime.now());

        Subscriber confirmed = subscriberRepository.save(subscriber);

        // Send welcome email asynchronously
        emailService.sendWelcomeEmail(
                confirmed.getEmail(),
                confirmed.getFullName(),
                confirmed.getToken());

        log.info("Subscription confirmed: {}", confirmed.getEmail());
        return SubscriberResponse.fromEntity(confirmed);
    }

    // UNSUBSCRIBE
    @Override
    @Transactional
    public void unsubscribe(String token) {
        log.info("🚪 Unsubscribe with token: {}...",
                token.substring(0, 8));

        Subscriber subscriber = subscriberRepository
                .findByToken(token)
                .orElseThrow(() -> new CustomException(
                        "Invalid unsubscribe link.",
                        HttpStatus.NOT_FOUND));

        if (subscriber.getStatus() ==
                Subscriber.SubscriberStatus.UNSUBSCRIBED) {
            // Already unsubscribed — idempotent (no error)
            log.info("Already unsubscribed: {}", subscriber.getEmail());
            return;
        }

        subscriber.setStatus(Subscriber.SubscriberStatus.UNSUBSCRIBED);
        subscriber.setUnsubscribedAt(LocalDateTime.now());
        subscriberRepository.save(subscriber);

        log.info("Unsubscribed: {}", subscriber.getEmail());
    }

    // GET SUBSCRIBER BY EMAIL
    @Override
    public SubscriberResponse getSubscriberByEmail(String email) {
        Subscriber sub = subscriberRepository.findByEmail(email)
                .orElseThrow(() -> new CustomException(
                        "Subscriber not found: " + email,
                        HttpStatus.NOT_FOUND));
        return SubscriberResponse.fromEntity(sub);
    }

    // GET ALL SUBSCRIBERS
    @Override
    public List<SubscriberResponse> getAllSubscribers() {
        return subscriberRepository.findAll()
                .stream()
                .map(SubscriberResponse::fromEntity)
                .collect(Collectors.toList());
    }

    // GET ACTIVE SUBSCRIBERS
    @Override
    public List<SubscriberResponse> getActiveSubscribers() {
        return subscriberRepository
                .findByStatus(Subscriber.SubscriberStatus.ACTIVE)
                .stream()
                .map(SubscriberResponse::fromEntity)
                .collect(Collectors.toList());
    }

    // SEND NEWSLETTER CAMPAIGN
    @Override
    @Async
    public void sendNewsletter(SendNewsletterRequest request) {
        log.info("📨 Starting newsletter campaign: {}",
                request.getSubject());

        // Get target subscribers (filtered or all)
        List<Subscriber> subscribers;

        if (request.getTargetPreference() != null &&
                !request.getTargetPreference().isBlank()) {
            subscribers = subscriberRepository.findActiveByPreference(
                    request.getTargetPreference());
            log.info("Targeting preference '{}': {} subscribers",
                    request.getTargetPreference(), subscribers.size());
        } else {
            subscribers = subscriberRepository.findByStatus(
                    Subscriber.SubscriberStatus.ACTIVE);
            log.info("Sending to all {} active subscribers",
                    subscribers.size());
        }

        if (subscribers.isEmpty()) {
            log.info("No subscribers to send campaign to.");
            return;
        }

        // Send in batches
        int totalSent = 0;
        int totalFailed = 0;

        for (int i = 0; i < subscribers.size(); i += batchSize) {
            int end = Math.min(i + batchSize, subscribers.size());
            List<Subscriber> batch = subscribers.subList(i, end);

            for (Subscriber sub : batch) {
                try {
                    emailService.sendNewsletterEmail(
                            sub.getEmail(),
                            sub.getFullName(),
                            sub.getToken(),
                            request.getSubject(),
                            request.getContent());
                    totalSent++;
                } catch (Exception e) {
                    log.error("Failed to send to {}: {}",
                            sub.getEmail(), e.getMessage());
                    totalFailed++;
                }
            }

            // Pause between batches (rate limiting)
            if (end < subscribers.size()) {
                try {
                    Thread.sleep(batchDelayMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        log.info("Campaign complete: sent={} failed={}",
                totalSent, totalFailed);
    }

    // NEW POST NOTIFICATION (RabbitMQ Consumer)

    @Override
    @RabbitListener(queues =
            RabbitMQConfig.POST_PUBLISHED_NEWSLETTER_QUEUE)
    @Async
    public void sendNewPostNotification(
            Map<String, Object> postEvent) {

        log.info("Received post.published event: postId={}",
                postEvent.get("postId"));

        String title   = (String) postEvent.get("title");
        String slug    = (String) postEvent.get("slug");
        String excerpt = (String) postEvent.get("excerpt");

        if (title == null || slug == null) {
            log.error("Invalid post event — missing title or slug");
            return;
        }

        List<Subscriber> activeSubscribers =
                subscriberRepository.findByStatus(
                        Subscriber.SubscriberStatus.ACTIVE);

        if (activeSubscribers.isEmpty()) {
            log.info("No active subscribers for new post notification.");
            return;
        }

        log.info("Sending new post notification to {} subscribers",
                activeSubscribers.size());

        // Send in batches (same rate limiting as campaigns)
        int sent = 0;
        for (int i = 0; i < activeSubscribers.size(); i += batchSize) {
            int end = Math.min(i + batchSize, activeSubscribers.size());
            List<Subscriber> batch = activeSubscribers.subList(i, end);

            for (Subscriber sub : batch) {
                try {
                    emailService.sendNewPostNotification(
                            sub.getEmail(),
                            sub.getFullName(),
                            sub.getToken(),
                            title,
                            excerpt,
                            slug);
                    sent++;
                } catch (Exception e) {
                    log.error("Failed to notify {}: {}",
                            sub.getEmail(), e.getMessage());
                }
            }

            if (end < activeSubscribers.size()) {
                try { Thread.sleep(batchDelayMs); }
                catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        log.info("New post notifications sent: {}/{} subscribers",
                sent, activeSubscribers.size());
    }

    // UPDATE PREFERENCES
    @Override
    @Transactional
    public SubscriberResponse updatePreferences(
            Integer subscriberId, String preferences) {

        Subscriber sub = subscriberRepository
                .findById(subscriberId)
                .orElseThrow(() -> new CustomException(
                        "Subscriber not found", HttpStatus.NOT_FOUND));

        subscriberRepository.updatePreferences(
                subscriberId, preferences);

        sub.setPreferences(preferences);
        return SubscriberResponse.fromEntity(sub);
    }

    // GET STATS
    @Override
    public Map<String, Long> getStats() {
        Map<String, Long> stats = new HashMap<>();
        stats.put("total", subscriberRepository.count());
        stats.put("active", subscriberRepository.countByStatus(
                Subscriber.SubscriberStatus.ACTIVE));
        stats.put("pending", subscriberRepository.countByStatus(
                Subscriber.SubscriberStatus.PENDING));
        stats.put("unsubscribed", subscriberRepository.countByStatus(
                Subscriber.SubscriberStatus.UNSUBSCRIBED));
        return stats;
    }

    // PRIVATE HELPER

    /*
     * generateToken():
     * UUID = Universally Unique Identifier.
     * 36-character string: "a1b2c3d4-e5f6-7890-abcd-ef1234567890"
     * 2^122 possible values → practically impossible to guess.
     * randomUUID() is cryptographically secure.
     */
    private String generateToken() {
        return UUID.randomUUID().toString();
    }
}