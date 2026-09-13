package com.electronics.store.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "imageUrl required, nonblank, max 500; stored as URL text, no upload. altText max 255. primary optional. displayOrder >= 0, defaults 0.")
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
