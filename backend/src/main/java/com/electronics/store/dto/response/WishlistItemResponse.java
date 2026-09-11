package com.electronics.store.dto.response;

import com.electronics.store.entity.ProductStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record WishlistItemResponse(Long id, Long productId, String productName, String slug,
                                   BigDecimal price, BigDecimal discountPrice, String thumbnailUrl,
                                   ProductStatus status, BrandSummaryResponse brand,
                                   CategorySummaryResponse category, LocalDateTime createdAt) {
}
