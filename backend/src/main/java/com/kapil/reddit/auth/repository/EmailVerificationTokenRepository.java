package com.kapil.reddit.auth.repository;

import com.kapil.reddit.auth.domain.EmailVerificationToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EmailVerificationTokenRepository extends JpaRepository<EmailVerificationToken, Long> {

    Optional<EmailVerificationToken> findByToken(String token);

    Optional<EmailVerificationToken> findTopByUser_IdOrderByIdDesc(Long userId);

    void deleteByUser_Id(Long userId);
}
