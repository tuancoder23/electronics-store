package com.electronics.store.dto.response;

import com.electronics.store.entity.ProductStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ProductResponse(
        Long id,
        String name,
        String slug,
        String description,
        BigDecimal price,
        BigDecimal discountPrice,
        Integer quantity,
        String thumbnailUrl,
        ProductStatus status,
        CategorySummaryResponse category,
        BrandSummaryResponse brand,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
