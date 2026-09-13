package com.electronics.store.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "Only fullName (required, nonblank, max 150) and phone (optional) accepted. Phone empty or 7..30 characters from digits, +, (, ), space, dot and hyphen. Unknown fields rejected; email/role/status cannot change.")
public record UpdateProfileRequest(
        @NotBlank(message = "Full name is required")
        @Size(max = 150, message = "Full name must not exceed 150 characters") String fullName,
        @Pattern(regexp = "^$|^[0-9+() .-]{7,30}$", message = "Phone number must be valid") String phone
) {
    @JsonAnySetter
    public void rejectUnknownField(String name, Object value) {
        throw new IllegalArgumentException("Unsupported profile field");
    }
}
