package com.electronics.store.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Replace quantity. JSON integer from 1 to 2147483647 within stock; strings/fractions rejected.")
public record UpdateCartItemRequest(
        @JsonDeserialize(using = QuantityDeserializer.class)
        @NotNull(message = "Quantity is required")
        @Min(value = 1, message = "Quantity must be at least 1") Integer quantity
) {
}
