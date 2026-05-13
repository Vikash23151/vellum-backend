package com.vellum.newsletter.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "subscribers",
        indexes = {
                @Index(name = "idx_subscriber_email",
                        columnList = "email",
                        unique = true),
                @Index(name = "idx_subscriber_token",
                        columnList = "token"),
                @Index(name = "idx_subscriber_status",
                        columnList = "status"),
                @Index(name = "idx_subscriber_user_id",
                        columnList = "user_id")
        }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Subscriber {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "subscriber_id")
    private Integer subscriberId;

    /*
     * email: unique identifier for subscribers.
     * One email = one subscriber record.
     * If already subscribed → show "already subscribed" message.
     */
    @Column(unique = true, nullable = false, length = 200)
    private String email;

    /*
     * userId: links to auth-service user (optional).
     * If subscriber is also a registered user.
     * null = anonymous subscriber (only has email).
     */
    @Column(name = "user_id")
    private Integer userId;

    @Column(name = "full_name", length = 100)
    private String fullName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private SubscriberStatus status = SubscriberStatus.PENDING;

    /*
     * token: UUID used for confirmation and unsubscribe links.
     * Same token for both operations (simpler).
     * Length 36: UUID format "xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"
     *
     * unique = true: each subscriber has exactly one token.
     * Prevents token collisions (important for security).
     */
    @Column(unique = true, nullable = false, length = 36)
    private String token;

    /*
     * tokenCreatedAt: when the token was generated.
     * Confirmation tokens expire after 24 hours.
     * After expiry: user must re-subscribe to get a new token.
     *
     * We check: now > tokenCreatedAt + 24 hours → expired
     */
    @Column(name = "token_created_at")
    private LocalDateTime tokenCreatedAt;

    /*
     * preferences: user's content preferences.
     * Stored as comma-separated string for simplicity.
     * Example: "java,spring-boot,tutorials"
     *
     * Admin can filter campaign by preference:
     * "Send only to subscribers with 'java' preference"
     */
    @Column(length = 500)
    @Builder.Default
    private String preferences = "";

    @Column(name = "subscribed_at")
    private LocalDateTime subscribedAt;

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    @Column(name = "unsubscribed_at")
    private LocalDateTime unsubscribedAt;

    @PrePersist
    protected void onCreate() {
        tokenCreatedAt = LocalDateTime.now();
    }

    public enum SubscriberStatus {
        PENDING,        // Email not confirmed yet
        ACTIVE,         // Confirmed, receives emails
        UNSUBSCRIBED    // Opted out, no emails
    }
}