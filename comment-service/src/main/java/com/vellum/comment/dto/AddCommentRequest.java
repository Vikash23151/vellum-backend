package com.vellum.comment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AddCommentRequest {

    @NotNull(message = "Post ID is required")
    private Integer postId;

    private Integer parentCommentId;

    @NotBlank(message = "Comment content cannot be empty")
    @Size(
        min = 1,
        max = 2000,
        message = "Comment must be between 1 and 2000 characters"
    )
    private String content;
}
