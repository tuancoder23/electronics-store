package com.electronics.store.dto.response;

import java.time.LocalDateTime;

public record ProductImageResponse(
        Long id,
        Long productId,
        String imageUrl,
        String altText,
        boolean primary,
        Integer displayOrder,
        LocalDateTime createdAt
) {
}
