package com.electronics.store.dto.request;

import com.fasterxml.jackson.annotation.JsonAnySetter;

// Validate password values in the service so validation exceptions do not contain rejected credentials.
public record ChangePasswordRequest(String currentPassword, String newPassword, String confirmPassword) {
    @JsonAnySetter
    public void rejectUnknownField(String name, Object value) {
        throw new IllegalArgumentException("Unsupported password change field");
    }

    @Override
    public String toString() {
        return "ChangePasswordRequest[credentials redacted]";
    }
}
