package com.vellum.category.dto;

import com.vellum.category.entity.Category;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CategoryResponse {

    private Integer categoryId;
    private String  name;
    private String  slug;
    private String  description;
    private Integer parentCategoryId;
    private String  parentName;
    private Integer postCount;
    private LocalDateTime createdAt;
    private List<CategoryResponse> children;

    public static CategoryResponse fromEntity(Category c) {
        return CategoryResponse.builder()
                .categoryId(c.getCategoryId())
                .name(c.getName())
                .slug(c.getSlug())
                .description(c.getDescription())
                .parentCategoryId(c.getParentCategoryId())
                .postCount(c.getPostCount())
                .createdAt(c.getCreatedAt())
                .build();
    }
}
