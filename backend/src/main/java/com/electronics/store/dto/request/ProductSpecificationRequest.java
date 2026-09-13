package com.electronics.store.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "specName required, nonblank, max 100 and unique per product. specValue required, nonblank, max 500. displayOrder >= 0, defaults 0.")
public record ProductSpecificationRequest(
        @NotBlank(message = "Specification name is required")
        @Size(min = 1, max = 100, message = "Specification name must not exceed 100 characters")
        String specName,

        @NotBlank(message = "Specification value is required")
        @Size(min = 1, max = 500, message = "Specification value must not exceed 500 characters")
        String specValue,

        @Min(value = 0, message = "Display order must be greater than or equal to 0")
        Integer displayOrder
) {
}
