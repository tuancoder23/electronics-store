package com.electronics.store.service.impl;

import com.electronics.store.dto.request.LoginRequest;
import com.electronics.store.dto.request.RegisterRequest;
import com.electronics.store.dto.response.AuthResponse;
import com.electronics.store.entity.Role;
import com.electronics.store.entity.UserEntity;
import com.electronics.store.entity.UserStatus;
import com.electronics.store.exception.DuplicateResourceException;
import com.electronics.store.mapper.UserMapper;
import com.electronics.store.repository.UserRepository;
import com.electronics.store.security.CustomUserDetails;
import com.electronics.store.security.JwtService;
import com.electronics.store.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {
    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    @Override
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmail(email)) {
            throw new DuplicateResourceException("Email already exists: " + email);
        }

        UserEntity user = UserEntity.builder()
                .fullName(request.fullName().trim())
                .email(email)
                .password(passwordEncoder.encode(request.password()))
                .phone(request.phone() == null || request.phone().isBlank() ? null : request.phone().trim())
                .role(Role.USER)
                .status(UserStatus.ACTIVE)
                .build();
        UserEntity saved = userRepository.save(user);
        String token = jwtService.generateToken(new CustomUserDetails(saved));
        return new AuthResponse(token, "Bearer", userMapper.toResponse(saved));
    }

    @Override
    public AuthResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(normalizeEmail(request.email()), request.password()));
        CustomUserDetails principal = (CustomUserDetails) authentication.getPrincipal();
        return new AuthResponse(jwtService.generateToken(principal), "Bearer", userMapper.toResponse(principal.getUser()));
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
