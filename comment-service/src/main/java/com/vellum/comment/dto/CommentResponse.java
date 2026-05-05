package com.vellum.comment.dto;

import com.vellum.comment.entity.Comment;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommentResponse {

    private Integer commentId;
    private Integer postId;
    private Integer authorId;
    private String authorUsername;
    private String authorAvatarUrl;
    private Integer parentCommentId;
    private String content;
    private Integer likesCount;
    private String status;
    private boolean isEdited;
    private boolean isLikedByCurrentUser;
    private boolean isOwner;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<CommentResponse> replies;

    public static CommentResponse fromEntity(Comment comment) {
        return CommentResponse.builder()
                .commentId(comment.getCommentId())
                .postId(comment.getPostId())
                .authorId(comment.getAuthorId())
                .parentCommentId(comment.getParentCommentId())
                .content(comment.getStatus() == Comment.CommentStatus.DELETED
                        ? "This comment has been deleted"
                        : comment.getContent())
                .likesCount(comment.getLikesCount())
                .status(comment.getStatus().name())
                .isEdited(comment.isEdited())
                .createdAt(comment.getCreatedAt())
                .updatedAt(comment.getUpdatedAt())
                .build();
    }
}
