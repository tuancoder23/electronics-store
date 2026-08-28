package com.electronics.store.mapper;

import com.electronics.store.dto.response.UserResponse;
import com.electronics.store.entity.UserEntity;
import org.springframework.stereotype.Component;

@Component
public class UserMapper {
    public UserResponse toResponse(UserEntity entity) {
        if (entity == null) return null;
        return new UserResponse(entity.getId(), entity.getFullName(), entity.getEmail(), entity.getPhone(),
                entity.getRole(), entity.getStatus(), entity.getCreatedAt(), entity.getUpdatedAt());
    }
}
