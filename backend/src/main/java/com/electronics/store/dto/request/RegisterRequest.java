package com.electronics.store.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "fullName required, nonblank, max 150; email required, valid, max 255 and unique after normalization. password required, 8..72 characters and <= 72 UTF-8 bytes. Optional phone empty or 7..30 characters from digits, +, (, ), space, dot and hyphen. Always creates USER/ACTIVE.")
public record RegisterRequest(
        @NotBlank(message = "Full name is required")
        @Size(max = 150, message = "Full name must not exceed 150 characters")
        String fullName,
        @NotBlank(message = "Email is required")
        @Email(message = "Email must be valid")
        @Size(max = 255, message = "Email must not exceed 255 characters")
        String email,
        @NotBlank(message = "Password is required")
        @Size(min = 8, max = 72, message = "Password must be between 8 and 72 characters")
        @Schema(format = "password", accessMode = Schema.AccessMode.WRITE_ONLY)
        String password,
        @Pattern(regexp = "^$|^[0-9+() .-]{7,30}$", message = "Phone number must be valid")
        String phone
) {
    @Override
    public String toString() {
        return "RegisterRequest[credentials redacted]";
    }
}
