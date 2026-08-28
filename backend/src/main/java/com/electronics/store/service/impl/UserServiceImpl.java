package com.electronics.store.service.impl;

import com.electronics.store.dto.response.UserResponse;
import com.electronics.store.entity.UserEntity;
import com.electronics.store.exception.ResourceNotFoundException;
import com.electronics.store.mapper.UserMapper;
import com.electronics.store.repository.UserRepository;
import com.electronics.store.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserServiceImpl implements UserService {
    private final UserRepository userRepository;
    private final UserMapper userMapper;

    @Override
    public UserResponse getByEmail(String email) {
        UserEntity user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        return userMapper.toResponse(user);
    }
}
