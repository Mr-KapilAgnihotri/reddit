package com.kapil.reddit.auth.service;

import com.kapil.reddit.auth.dto.LoginRequest;
import com.kapil.reddit.auth.dto.LoginResponse;
import com.kapil.reddit.auth.security.JwtService;
import com.kapil.reddit.common.exception.BusinessException;
import com.kapil.reddit.user.domain.User;
import com.kapil.reddit.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public LoginResponse login(LoginRequest request) {

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new BusinessException("Invalid email or password"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new BusinessException("Invalid email or password");
        }

        String token = jwtService.generateToken(user.getEmail());

        return LoginResponse.builder()
                .access_token(token)
                .token_type("Bearer")
                .build();
    }

}
