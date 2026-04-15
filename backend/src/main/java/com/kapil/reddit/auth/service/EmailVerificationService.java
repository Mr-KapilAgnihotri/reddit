package com.kapil.reddit.auth.service;

import com.kapil.reddit.auth.domain.EmailVerificationToken;
import com.kapil.reddit.auth.repository.EmailVerificationTokenRepository;
import com.kapil.reddit.common.exception.BusinessException;
import com.kapil.reddit.common.exception.ResourceNotFoundException;
import com.kapil.reddit.user.domain.User;
import com.kapil.reddit.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailVerificationService {

    private final EmailVerificationTokenRepository emailVerificationTokenRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;

    @Value("${app.base-url}")
    private String appBaseUrl;

    @Transactional
    public String initiateForNewUser(User user) {
        emailVerificationTokenRepository.deleteByUser_Id(user.getId());

        String rawToken = UUID.randomUUID().toString();
        EmailVerificationToken entity = EmailVerificationToken.builder()
                .user(user)
                .token(rawToken)
                .expiresAt(Instant.now().plus(24, ChronoUnit.HOURS))
                .createdAt(Instant.now())
                .build();
        emailVerificationTokenRepository.save(entity);

        String link = buildBaseUrl() + "/api/auth/verify?token=" + rawToken;
        String body = "Verify your email by opening this link in your browser:\n" + link
                + "\n\nOr send POST /api/auth/verify with this token in the body or query.";

        try {
            emailService.sendEmail(user.getEmail(), "Verify your email", body);
        } catch (Exception e) {
            log.warn("Failed to send verification email to user id {}: {}", user.getId(), e.getMessage());
        }
        return rawToken;
    }

    @Transactional
    public void verifyEmail(String token) {
        EmailVerificationToken verificationToken = emailVerificationTokenRepository.findByToken(token)
                .orElseThrow(() -> new ResourceNotFoundException("Verification token not found"));

        if (verificationToken.getExpiresAt().isBefore(Instant.now())) {
            throw new BusinessException("Verification token has expired");
        }

        User user = verificationToken.getUser();
        user.setIsVerified(true);
        userRepository.save(user);
        emailVerificationTokenRepository.delete(verificationToken);
    }

    private String buildBaseUrl() {
        String base = appBaseUrl == null ? "" : appBaseUrl.trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base;
    }
}
