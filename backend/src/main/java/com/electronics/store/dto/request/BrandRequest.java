package com.electronics.store.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "name required, nonblank, 2..100 characters and unique after trimming. description/logoUrl optional, max 500 each.")
public record BrandRequest(
        @NotBlank(message = "Brand name is required")
        @Size(min = 2, max = 100, message = "Brand name must be between 2 and 100 characters")
        String name,

        @Size(max = 500, message = "Description must not exceed 500 characters")
        String description,

        @Size(max = 500, message = "Logo URL must not exceed 500 characters")
        String logoUrl
) {
}
