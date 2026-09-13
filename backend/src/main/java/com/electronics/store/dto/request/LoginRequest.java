package com.electronics.store.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Valid nonblank email and nonblank password required. Email normalized; password whitespace preserved.")
public record LoginRequest(
        @NotBlank(message = "Email is required")
        @Email(message = "Email must be valid")
        String email,
        @NotBlank(message = "Password is required")
        @Schema(format = "password", accessMode = Schema.AccessMode.WRITE_ONLY)
        String password
) {
    @Override
    public String toString() {
        return "LoginRequest[credentials redacted]";
    }
}
