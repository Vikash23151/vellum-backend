package com.vellum.category.dto;

import com.vellum.category.entity.Tag;
import lombok.*;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TagResponse {

    private Integer tagId;
    private String  name;
    private String  slug;
    private Integer postCount;
    private LocalDateTime createdAt;

    public static TagResponse fromEntity(Tag tag) {
        return TagResponse.builder()
                .tagId(tag.getTagId())
                .name(tag.getName())
                .slug(tag.getSlug())
                .postCount(tag.getPostCount())
                .createdAt(tag.getCreatedAt())
                .build();
    }
}
