package com.vellum.category.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateTagRequest {

    @NotBlank(message = "Tag name is required")
    @Size(min = 1, max = 100,
          message = "Tag name must be between 1 and 100 characters")
    @Pattern(
        regexp = "^[a-zA-Z0-9+\\-. ]+$",
        message = "Tag name can only contain letters, numbers, " +
                  "spaces, hyphens, plus signs, and periods"
    )
    private String name;
}
