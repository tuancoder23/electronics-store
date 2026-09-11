package com.electronics.store.dto.response;

public record ProductRatingSummaryResponse(Double averageRating, Long reviewCount) {
    public ProductRatingSummaryResponse {
        if (averageRating == null) averageRating = 0.0;
    }
}
