package com.electronics.store.dto.response;

import java.time.LocalDateTime;

public record CategoryResponse(
        Long id,
        String name,
        String slug,
        String description,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
