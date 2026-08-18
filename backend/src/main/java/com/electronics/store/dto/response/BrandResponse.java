package com.electronics.store.dto.response;

import java.time.LocalDateTime;

public record BrandResponse(
        Long id,
        String name,
        String slug,
        String description,
        String logoUrl,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
