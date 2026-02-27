package com.kapil.reddit.user.service;

import com.kapil.reddit.common.exception.BusinessException;
import com.kapil.reddit.user.domain.User;
import com.kapil.reddit.user.dto.CreateUserRequest;
import com.kapil.reddit.user.dto.UserResponse;
import com.kapil.reddit.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserResponse createUser(CreateUserRequest request) {

        if (userRepository.existsByUsername(request.getUsername())) {
            throw new BusinessException("Username already exists");
        }

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BusinessException("Email already exists");
        }

        String hashedPassword= passwordEncoder.encode(request.getPassword());
        User user= User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .passwordHash(hashedPassword)
                .build();

        User savedUser=userRepository.save(user);

        return UserResponse.builder()
                .id(savedUser.getId())
                .username(savedUser.getUsername())
                .email(savedUser.getEmail())
                .role(savedUser.getRole())
                .isActive(savedUser.getIsActive())
                .createdAt(savedUser.getCreatedAt())
                .build();
    }
}
