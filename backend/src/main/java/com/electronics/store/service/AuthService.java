package com.electronics.store.service;

import com.electronics.store.dto.request.LoginRequest;
import com.electronics.store.dto.request.RegisterRequest;
import com.electronics.store.dto.response.AuthResponse;

public interface AuthService {
    AuthResponse register(RegisterRequest request);
    AuthResponse login(LoginRequest request);
}
