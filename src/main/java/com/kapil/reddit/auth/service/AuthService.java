package com.kapil.reddit.auth.service;

import com.kapil.reddit.auth.domain.RefreshToken;
import com.kapil.reddit.auth.dto.LoginRequest;
import com.kapil.reddit.auth.dto.LoginResponse;
import com.kapil.reddit.auth.repository.RefreshTokenRepository;
import com.kapil.reddit.auth.security.JwtService;
import com.kapil.reddit.common.exception.BusinessException;
import com.kapil.reddit.user.domain.User;
import com.kapil.reddit.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final RefreshTokenRepository refreshTokenRepository;

    public LoginResponse login(LoginRequest request) {

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new BusinessException("Invalid email or password"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new BusinessException("Invalid email or password");
        }

        String accessToken = jwtService.generateAccessToken(
                user.getEmail(),
                user.getRoles()
        );

        RefreshToken refreshToken = refreshTokenService.createRefreshToken(user);

        return LoginResponse.builder()
                .access_token(accessToken)
                .refresh_token(refreshToken.getToken())
                .token_type("Bearer")
                .build();

    }

    public LoginResponse refresh(String refreshTokenValue) {

        RefreshToken refreshToken =
                refreshTokenService.verifyRefreshToken(refreshTokenValue);

        User user = refreshToken.getUser();

        RefreshToken newRefreshToken =
                refreshTokenService.rotateRefreshToken(refreshToken);

        String newAccessToken =
                jwtService.generateAccessToken(
                        user.getEmail(),
                        user.getRoles()
                );

        return LoginResponse.builder()
                .access_token(newAccessToken)
                .refresh_token(newRefreshToken.getToken())
                .token_type("Bearer")
                .build();
    }

    public void logout(String refreshTokenValue) {

        RefreshToken refreshToken =
                refreshTokenRepository.findByToken(refreshTokenValue)
                        .orElseThrow(() -> new BusinessException("Invalid refresh token"));

        if (refreshToken.isRevoked()) {
            throw new BusinessException("Token already revoked");
        }

        refreshToken.setRevoked(true);
        refreshTokenRepository.save(refreshToken);
    }


    public void logoutAll(String email) {

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException("User not found"));

        List<RefreshToken> tokens =
                refreshTokenRepository.findAllByUser(user);

        for (RefreshToken token : tokens) {
            if (token.isRevoked()) {
                throw new BusinessException("Token already revoked");
            }
            token.setRevoked(true);
        }

        refreshTokenRepository.saveAll(tokens);
    }
}
