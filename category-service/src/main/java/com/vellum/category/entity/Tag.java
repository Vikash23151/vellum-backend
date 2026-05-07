package com.vellum.category.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(
    name = "tags",
    indexes = {
        @Index(name = "idx_tag_slug",
               columnList = "slug"),
        @Index(name = "idx_tag_post_count",
               columnList = "post_count")
    }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Tag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "tag_id")
    private Integer tagId;

    @Column(unique = true, nullable = false, length = 100)
    private String name;

    @Column(unique = true, nullable = false, length = 150)
    private String slug;

    @Column(name = "post_count")
    @Builder.Default
    private Integer postCount = 0;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
