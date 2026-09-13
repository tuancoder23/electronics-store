package com.electronics.store.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

import com.fasterxml.jackson.annotation.JsonAnySetter;

// Validate password values in the service so validation exceptions do not contain rejected credentials.
@Schema(description = "Only currentPassword/newPassword/confirmPassword accepted; all required and nonblank. Current password must match. New password matches confirmation, 8..72 characters and <= 72 UTF-8 bytes. Validation occurs in service, not Bean Validation. Unknown fields rejected.")
public record ChangePasswordRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, format = "password", accessMode = Schema.AccessMode.WRITE_ONLY) String currentPassword,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minLength = 8, maxLength = 72, format = "password", accessMode = Schema.AccessMode.WRITE_ONLY) String newPassword,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, format = "password", accessMode = Schema.AccessMode.WRITE_ONLY) String confirmPassword) {
    @JsonAnySetter
    public void rejectUnknownField(String name, Object value) {
        throw new IllegalArgumentException("Unsupported password change field");
    }

    @Override
    public String toString() {
        return "ChangePasswordRequest[credentials redacted]";
    }
}
