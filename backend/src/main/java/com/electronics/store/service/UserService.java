package com.electronics.store.service;

import com.electronics.store.dto.response.UserResponse;

public interface UserService {
    UserResponse getByEmail(String email);
}
