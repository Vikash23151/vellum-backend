package com.vellum.category.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateCategoryRequest {

    @Size(min = 2, max = 100)
    private String name;

    @Size(max = 500)
    private String description;

    private Integer parentCategoryId;
}
