package com.electronics.store.service;

import com.electronics.store.dto.request.ChangePasswordRequest;
import com.electronics.store.dto.request.UpdateProfileRequest;
import com.electronics.store.dto.response.UserResponse;

public interface UserService {
    UserResponse getCurrentUser();
    UserResponse updateCurrentUser(UpdateProfileRequest request);
    void changeCurrentUserPassword(ChangePasswordRequest request);
}
