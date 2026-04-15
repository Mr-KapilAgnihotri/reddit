package com.kapil.reddit.auth.service;

import com.kapil.reddit.auth.domain.RefreshToken;
import com.kapil.reddit.auth.repository.RefreshTokenRepository;
import com.kapil.reddit.common.exception.BusinessException;
import com.kapil.reddit.user.domain.User;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;

    @Value("${jwt.refresh-token-expiration}")
    private long refreshTokenExpiration;

    public RefreshToken createRefreshToken(User user) {

        RefreshToken refreshToken = RefreshToken.builder()
                .token(UUID.randomUUID().toString())
                .user(user)
                .expiryDate(Instant.now().plusMillis(refreshTokenExpiration))
                .revoked(false)
                .createdAt(Instant.now())
                .build();

        return refreshTokenRepository.save(refreshToken);
    }

    public RefreshToken verifyRefreshToken(String token) {

        RefreshToken refreshToken = refreshTokenRepository.findByToken(token)
                .orElseThrow(() -> new BusinessException("Invalid refresh token"));

        if (refreshToken.isRevoked()) {

            // REUSE DETECTED
            revokeAllUserTokens(refreshToken.getUser());

            throw new BusinessException("Refresh token reuse detected. All sessions revoked.");
        }

        if (refreshToken.getExpiryDate().isBefore(Instant.now())) {
            throw new BusinessException("Refresh token expired");
        }

        return refreshToken;
    }

    private void revokeAllUserTokens(User user) {

        List<RefreshToken> tokens =
                refreshTokenRepository.findAllByUser(user);

        for (RefreshToken token : tokens) {
            token.setRevoked(true);
        }

        refreshTokenRepository.saveAll(tokens);
    }

    public RefreshToken rotateRefreshToken(RefreshToken oldToken) {

        oldToken.setRevoked(true);
        refreshTokenRepository.save(oldToken);

        return createRefreshToken(oldToken.getUser());
    }



}
