package com.kapil.reddit.user.service;

import com.kapil.reddit.auth.service.EmailVerificationService;
import com.kapil.reddit.common.exception.BusinessException;
import com.kapil.reddit.user.domain.Role;
import com.kapil.reddit.user.domain.User;
import com.kapil.reddit.user.dto.CreateUserRequest;
import com.kapil.reddit.user.dto.UserResponse;
import com.kapil.reddit.user.repository.RoleRepository;
import com.kapil.reddit.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final RoleRepository roleRepository;
    private final EmailVerificationService emailVerificationService;

    @Value("${app.auth.expose-verification-token-on-register:false}")
    private boolean exposeVerificationTokenOnRegister;

    public UserResponse createUser(CreateUserRequest request) {

        if (userRepository.existsByUsername(request.getUsername())) {
            throw new BusinessException("Username already exists");
        }

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BusinessException("Email already exists");
        }

        String hashedPassword = passwordEncoder.encode(request.getPassword());

        User user = User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .passwordHash(hashedPassword)
                .isActive(true)
                .build();

        Role userRole = roleRepository.findByName("USER")
                .orElseThrow(() -> new BusinessException("Default role USER not found"));

        user.getRoles().add(userRole);

        User savedUser = userRepository.save(user);

        String verificationToken = emailVerificationService.initiateForNewUser(savedUser);

        UserResponse.UserResponseBuilder response = UserResponse.builder()
                .id(savedUser.getId())
                .username(savedUser.getUsername())
                .email(savedUser.getEmail())
                .roles(
                        savedUser.getRoles()
                                .stream()
                                .map(Role::getName)
                                .toList()
                )
                .isActive(savedUser.getIsActive())
                .createdAt(savedUser.getCreatedAt());
        if (exposeVerificationTokenOnRegister) {
            response.verificationToken(verificationToken);
        }
        return response.build();
    }

    public void updateUserRoles(Long userId, List<String> roleNames) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("User not found"));

        Set<Role> roles = roleNames.stream()
                .map(roleName -> roleRepository.findByName(roleName)
                        .orElseThrow(() -> new BusinessException("Role not found: " + roleName)))
                .collect(Collectors.toSet());

        user.setRoles(roles);

        userRepository.save(user);
    }

}
