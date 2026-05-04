package com.vellum.post.dto;

import com.vellum.post.entity.Post;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PostResponse {

    private Integer postId;
    private Integer authorId;
    private String authorName;      // fetched from auth-service
    private String authorUsername;  // fetched from auth-service
    private String authorAvatarUrl; // fetched from auth-service
    private String title;
    private String slug;
    private String content;         // null in feed listings (bandwidth saving)
    private String excerpt;
    private String featuredImageUrl;
    private String status;
    private Integer readTimeMin;
    private String readTimeFormatted;  // "5 min read"
    private Integer viewCount;
    private Integer likesCount;
    private boolean isFeatured;
    private boolean isLikedByCurrentUser;  // true if requesting user liked this
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime publishedAt;

    /*
     * Static factory: converts Post entity to PostResponse.
     * Used in service layer before returning to controller.
     */
    public static PostResponse fromEntity(Post post) {
        return PostResponse.builder()
                .postId(post.getPostId())
                .authorId(post.getAuthorId())
                .title(post.getTitle())
                .slug(post.getSlug())
                .excerpt(post.getExcerpt())
                .featuredImageUrl(post.getFeaturedImageUrl())
                .status(post.getStatus().name())
                .readTimeMin(post.getReadTimeMin())
                .readTimeFormatted(post.getReadTimeMin() + " min read")
                .viewCount(post.getViewCount())
                .likesCount(post.getLikesCount())
                .isFeatured(post.isFeatured())
                .createdAt(post.getCreatedAt())
                .updatedAt(post.getUpdatedAt())
                .publishedAt(post.getPublishedAt())
                .build();
    }

    // fromEntity with content included (for single post view, not feed)
    public static PostResponse fromEntityWithContent(Post post) {
        PostResponse response = fromEntity(post);
        response.setContent(post.getContent());
        return response;
    }
}