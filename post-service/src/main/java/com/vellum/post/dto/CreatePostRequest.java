package com.vellum.post.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class CreatePostRequest {

    @NotBlank(message = "Title is required")
    @Size(min = 5, max = 300, message = "Title must be between 5 and 300 characters")
    private String title;

    private String content;

    @Size(max = 500, message = "Excerpt cannot exceed 500 characters")
    private String excerpt;

    private String featuredImageUrl;

    private List<Integer> categoryIds;

    private List<String> tags;

    private boolean publishImmediately = false;
}