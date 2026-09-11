package com.electronics.store.dto.response;

public record AuthResponse(String accessToken, String tokenType, UserResponse user) {
    @Override
    public String toString() {
        return "AuthResponse[credentials redacted]";
    }
}
