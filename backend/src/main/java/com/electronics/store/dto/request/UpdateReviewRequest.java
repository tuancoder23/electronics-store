package com.electronics.store.dto.request;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.*;

public record UpdateReviewRequest(
        @JsonDeserialize(using = ReviewRatingDeserializer.class)
        @NotNull(message = "Rating is required")
        @Min(value = 1, message = "Rating must be between 1 and 5")
        @Max(value = 5, message = "Rating must be between 1 and 5") Integer rating,
        @NotBlank(message = "Comment is required")
        @Size(min = 1, max = 1000, message = "Comment must be between 1 and 1000 characters") String comment
) {
    public UpdateReviewRequest {
        if (comment != null) comment = comment.strip();
    }

    @JsonAnySetter
    public void rejectUnknownField(String name, Object value) {
        throw new IllegalArgumentException("Unsupported review field");
    }
}
