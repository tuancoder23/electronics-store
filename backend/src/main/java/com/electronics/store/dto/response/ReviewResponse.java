package com.electronics.store.dto.response;

import java.time.LocalDateTime;

public record ReviewResponse(
        Long id,
        UserSummary user,
        Integer rating,
        String comment,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public record UserSummary(Long id, String fullName) { }
}
