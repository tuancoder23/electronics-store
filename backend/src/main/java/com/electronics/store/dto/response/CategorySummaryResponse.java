package com.electronics.store.dto.response;

public record CategorySummaryResponse(
        Long id,
        String name,
        String slug
) {
}
