package com.vellum.category.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(
    name = "post_tag_associations",
    uniqueConstraints = {
        @UniqueConstraint(
            name  = "uk_post_tag",
            columnNames = {"post_id", "tag_id"}
        )
    },
    indexes = {
        @Index(name = "idx_post_tag_post_id",
               columnList = "post_id"),
        @Index(name = "idx_post_tag_tag_id",
               columnList = "tag_id")
    }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PostTagAssociation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "association_id")
    private Integer associationId;

    @Column(name = "post_id", nullable = false)
    private Integer postId;

    @Column(name = "tag_id", nullable = false)
    private Integer tagId;

    @Column(name = "tagged_at", updatable = false)
    private LocalDateTime taggedAt;

    @PrePersist
    protected void onCreate() {
        taggedAt = LocalDateTime.now();
    }
}
