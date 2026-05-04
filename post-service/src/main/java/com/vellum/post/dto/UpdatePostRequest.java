package com.vellum.post.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class UpdatePostRequest {

    @Size(min = 5, max = 300, message = "Title must be between 5 and 300 characters")
    private String title;       // null = don't update title

    private String content;

    @Size(max = 500)
    private String excerpt;

    private String featuredImageUrl;

    private List<Integer> categoryIds;

    private List<String> tags;
}