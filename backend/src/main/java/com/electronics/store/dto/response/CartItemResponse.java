package com.electronics.store.dto.response;

import java.math.BigDecimal;

public record CartItemResponse(
        Long id,
        Long productId,
        String productName,
        String slug,
        String thumbnailUrl,
        BigDecimal price,
        BigDecimal discountPrice,
        BigDecimal effectivePrice,
        Integer quantity,
        BigDecimal lineTotal
) {
}
