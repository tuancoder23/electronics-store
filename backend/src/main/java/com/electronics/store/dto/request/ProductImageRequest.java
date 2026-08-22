package com.electronics.store.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ProductImageRequest(
        @NotBlank(message = "Image URL is required")
        @Size(max = 500, message = "Image URL must not exceed 500 characters")
        String imageUrl,

        @Size(max = 255, message = "Alt text must not exceed 255 characters")
        String altText,

        Boolean primary,

        @Min(value = 0, message = "Display order must be greater than or equal to 0")
        Integer displayOrder
) {
}
