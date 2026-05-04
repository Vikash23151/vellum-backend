package com.vellum.post.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "posts",
        indexes = {
                @Index(name = "idx_post_slug",      columnList = "slug"),
                @Index(name = "idx_post_author",    columnList = "author_id"),
                @Index(name = "idx_post_status",    columnList = "status"),
                @Index(name = "idx_post_published", columnList = "published_at")
        })
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Post {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "post_id")
    private Integer postId;

    @Column(name = "author_id", nullable = false)
    private Integer authorId;

    @Column(nullable = false, length = 300)
    private String title;

    @Column(unique = true, nullable = false, length = 350)
    private String slug;

    @Column(columnDefinition = "MEDIUMTEXT")
    private String content;

    @Column(length = 500)
    private String excerpt;

    @Column(name = "featured_image_url", length = 1000)
    private String featuredImageUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private PostStatus status = PostStatus.DRAFT;

    @Column(name = "read_time_min")
    @Builder.Default
    private Integer readTimeMin = 1;

    @Column(name = "view_count")
    @Builder.Default
    private Integer viewCount = 0;

    @Column(name = "likes_count")
    @Builder.Default
    private Integer likesCount = 0;

    @Column(name = "is_featured")
    @Builder.Default
    private boolean isFeatured = false;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    // Enum
    public enum PostStatus {
        DRAFT,
        PUBLISHED,
        UNPUBLISHED,
        ARCHIVED
    }
}