package com.vellum.media.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateAltTextRequest {

    @Size(max = 500,
            message = "Alt text cannot exceed 500 characters")
    private String altText;
}