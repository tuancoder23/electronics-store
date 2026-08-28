package com.electronics.store.controller;

import com.electronics.store.dto.response.ApiResponse;
import com.electronics.store.dto.response.UserResponse;
import com.electronics.store.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserResponse>> getCurrentUser(Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.ok("Current user retrieved successfully",
                userService.getByEmail(authentication.getName())));
    }
}
