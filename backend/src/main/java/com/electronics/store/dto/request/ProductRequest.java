package com.electronics.store.dto.request;

import com.electronics.store.entity.ProductStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record ProductRequest(
        @NotBlank(message = "Product name is required")
        @Size(min = 2, max = 200, message = "Product name must be between 2 and 200 characters")
        String name,

        String description,

        @NotNull(message = "Price is required")
        @DecimalMin(value = "0.0", inclusive = true, message = "Price must be greater than or equal to 0")
        BigDecimal price,

        @DecimalMin(value = "0.0", inclusive = true, message = "Discount price must be greater than or equal to 0")
        BigDecimal discountPrice,

        @NotNull(message = "Quantity is required")
        @Min(value = 0, message = "Quantity must be greater than or equal to 0")
        Integer quantity,

        @Size(max = 500, message = "Thumbnail URL must not exceed 500 characters")
        String thumbnailUrl,

        ProductStatus status,

        @NotNull(message = "Category ID is required")
        Long categoryId,

        @NotNull(message = "Brand ID is required")
        Long brandId
) {
}
