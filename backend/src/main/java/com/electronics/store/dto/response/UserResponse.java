package com.electronics.store.dto.response;

import com.electronics.store.entity.Role;
import com.electronics.store.entity.UserStatus;

import java.time.LocalDateTime;

public record UserResponse(
        Long id,
        String fullName,
        String email,
        String phone,
        Role role,
        UserStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
