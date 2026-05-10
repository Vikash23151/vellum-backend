package com.vellum.media.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;


@Entity
@Table(
        name = "media_files",
        indexes = {
                @Index(name = "idx_media_uploader",
                        columnList = "uploader_id"),
                @Index(name = "idx_media_post",
                        columnList = "linked_post_id"),
                @Index(name = "idx_media_deleted",
                        columnList = "is_deleted")
        }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Media {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "media_id")
    private Integer mediaId;

    @Column(name = "uploader_id", nullable = false)
    private Integer uploaderId;

    @Column(nullable = false, length = 200)
    private String filename;

    @Column(name = "original_name", nullable = false, length = 200)
    private String originalName;

    @Column(nullable = false, length = 1000)
    private String url;

    @Column(name = "mime_type", nullable = false, length = 100)
    private String mimeType;

    @Column(name = "size_kb", nullable = false)
    private Long sizeKb;

    @Column(name = "alt_text", length = 500)
    @Builder.Default
    private String altText = "";

    @Column(name = "linked_post_id")
    private Integer linkedPostId;

    @Column(name = "uploaded_at", updatable = false)
    private LocalDateTime uploadedAt;

    @Column(name = "is_deleted", nullable = false)
    @Builder.Default
    private boolean isDeleted = false;

    @PrePersist
    protected void onCreate() {
        uploadedAt = LocalDateTime.now();
    }
}