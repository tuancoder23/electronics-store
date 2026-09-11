package com.electronics.store.mapper;

import com.electronics.store.dto.response.ReviewResponse;
import com.electronics.store.entity.ReviewEntity;
import org.springframework.stereotype.Component;

@Component
public class ReviewMapper {
    public ReviewResponse toResponse(ReviewEntity entity) {
        return new ReviewResponse(entity.getId(),
                new ReviewResponse.UserSummary(entity.getUser().getId(), entity.getUser().getFullName()),
                entity.getRating(), entity.getComment(), entity.getCreatedAt(), entity.getUpdatedAt());
    }
}
