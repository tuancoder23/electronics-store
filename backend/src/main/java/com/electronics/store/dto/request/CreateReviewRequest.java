package com.electronics.store.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.*;

@Schema(description = "Only rating and comment accepted. Rating required JSON integer 1..5 (no string/fraction coercion). Comment stripped before validation, nonblank, 1..1000 characters. Requires delivered purchase.")
public record CreateReviewRequest(
        @JsonDeserialize(using = ReviewRatingDeserializer.class)
        @NotNull(message = "Rating is required")
        @Min(value = 1, message = "Rating must be between 1 and 5")
        @Max(value = 5, message = "Rating must be between 1 and 5") Integer rating,
        @NotBlank(message = "Comment is required")
        @Size(min = 1, max = 1000, message = "Comment must be between 1 and 1000 characters") String comment
) {
    public CreateReviewRequest {
        if (comment != null) comment = comment.strip();
    }

    @JsonAnySetter
    public void rejectUnknownField(String name, Object value) {
        throw new IllegalArgumentException("Unsupported review field");
    }
}
