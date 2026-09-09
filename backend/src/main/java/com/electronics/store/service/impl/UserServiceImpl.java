package com.electronics.store.service.impl;

import com.electronics.store.dto.request.ChangePasswordRequest;
import com.electronics.store.dto.request.UpdateProfileRequest;
import com.electronics.store.dto.response.UserResponse;
import com.electronics.store.entity.UserEntity;
import com.electronics.store.exception.ForbiddenOperationException;
import com.electronics.store.exception.ResourceNotFoundException;
import com.electronics.store.mapper.UserMapper;
import com.electronics.store.repository.UserRepository;
import com.electronics.store.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserServiceImpl implements UserService {
    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    @Override
    public UserResponse getCurrentUser() {
        UserEntity user = userRepository.findByEmail(currentEmail())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        return userMapper.toResponse(user);
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public UserResponse updateCurrentUser(UpdateProfileRequest request) {
        UserEntity user = currentUserForUpdate();
        user.setFullName(request.fullName().trim());
        user.setPhone(request.phone() == null || request.phone().isBlank() ? null : request.phone().trim());
        return userMapper.toResponse(userRepository.saveAndFlush(user));
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void changeCurrentUserPassword(ChangePasswordRequest request) {
        if (request.currentPassword() == null || request.currentPassword().isBlank()) {
            throw new IllegalArgumentException("Current password is required");
        }
        UserEntity user = currentUserForUpdate();
        if (!passwordEncoder.matches(request.currentPassword(), user.getPassword())) {
            throw new IllegalArgumentException("Current password is incorrect");
        }
        String newPassword = request.newPassword();
        if (newPassword == null || newPassword.isBlank() || newPassword.length() < 8 || newPassword.length() > 72) {
            throw new IllegalArgumentException("New password must be between 8 and 72 characters and must not be blank");
        }
        // BCrypt limits bytes, while the registration policy specifies characters.
        if (newPassword.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalArgumentException("New password must not exceed 72 UTF-8 bytes");
        }
        if (!newPassword.equals(request.confirmPassword())) {
            throw new IllegalArgumentException("Password confirmation does not match");
        }
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.saveAndFlush(user);
    }

    private UserEntity currentUserForUpdate() {
        // Both writes use the same lock, preserving profile fields and the latest password hash.
        return userRepository.findByEmailForUpdate(currentEmail())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    private String currentEmail() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal())) {
            throw new ForbiddenOperationException("Authentication is required");
        }
        return authentication.getName();
    }
}
