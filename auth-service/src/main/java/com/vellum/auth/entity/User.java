package com.vellum.auth.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "users",
        indexes = {
                // DB indexes speed up lookups by email and username
                // Without indexes: full table scan on every login (slow)
                @Index(name = "idx_user_email",    columnList = "email"),
                @Index(name = "idx_user_username", columnList = "username")
        })
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Integer userId;

    @Column(unique = true, nullable = false, length = 50)
    private String username;

    @Column(unique = true, nullable = false, length = 100)
    private String email;

    @Column(name = "password_hash")
    private String passwordHash;

    @Column(name = "full_name", length = 100)
    private String fullName;

    /*
     * @Enumerated(STRING): store "READER"/"AUTHOR"/"ADMIN" as text in DB
     * Without STRING: stores 0/1/2 as ordinals (breaks if you reorder enum)
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private Role role = Role.READER;  // new users are always READER

    @Column(columnDefinition = "TEXT")
    private String bio;

    @Column(name = "avatar_url", length = 500)
    private String avatarUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private Provider provider = Provider.LOCAL;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean isActive = true;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    // Enums

    public enum Role {
        READER,   // can read, comment, like
        AUTHOR,   // can create posts + READER abilities
        ADMIN     // can do everything
    }

    public enum Provider {
        LOCAL,    // registered with email/password
        GOOGLE,   // logged in with Google OAuth
        GITHUB    // logged in with GitHub OAuth
    }
}