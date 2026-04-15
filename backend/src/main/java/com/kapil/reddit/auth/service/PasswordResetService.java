package com.kapil.reddit.auth.service;

import com.kapil.reddit.auth.domain.PasswordResetToken;
import com.kapil.reddit.auth.repository.PasswordResetTokenRepository;
import com.kapil.reddit.auth.repository.RefreshTokenRepository;
import com.kapil.reddit.common.exception.BusinessException;
import com.kapil.reddit.common.exception.ResourceNotFoundException;
import com.kapil.reddit.user.domain.User;
import com.kapil.reddit.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PasswordResetService {

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;

    @Value("${app.base-url}")
    private String appBaseUrl;

    @Transactional
    public void forgotPassword(String email) {
        userRepository.findByEmail(email).ifPresent(user -> {
            passwordResetTokenRepository.deleteByUser_Id(user.getId());

            String rawToken = UUID.randomUUID().toString();
            PasswordResetToken entity = PasswordResetToken.builder()
                    .user(user)
                    .token(rawToken)
                    .expiresAt(Instant.now().plus(1, ChronoUnit.HOURS))
                    .createdAt(Instant.now())
                    .build();
            passwordResetTokenRepository.save(entity);

            String link = buildBaseUrl() + "/api/auth/reset-password?token=" + rawToken;
            String body = "Reset your password using this link for reference, then call POST /api/auth/reset-password "
                    + "with JSON {\"token\":\"<token>\",\"newPassword\":\"...\"}:\n"
                    + link;

            try {
                emailService.sendEmail(user.getEmail(), "Password reset", body);
            } catch (Exception e) {
                log.warn("Failed to send password reset email to user id {}: {}", user.getId(), e.getMessage());
            }
        });
    }

    @Transactional
    public void resetPassword(String token, String newPassword) {
        PasswordResetToken resetToken = passwordResetTokenRepository.findByToken(token)
                .orElseThrow(() -> new ResourceNotFoundException("Password reset token not found"));

        if (resetToken.getExpiresAt().isBefore(Instant.now())) {
            throw new BusinessException("Password reset token has expired");
        }

        User user = resetToken.getUser();
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        passwordResetTokenRepository.delete(resetToken);
        refreshTokenRepository.deleteByUser(user);
    }

    private String buildBaseUrl() {
        String base = appBaseUrl == null ? "" : appBaseUrl.trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base;
    }
}
