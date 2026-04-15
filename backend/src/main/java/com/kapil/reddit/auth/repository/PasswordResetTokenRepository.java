package com.kapil.reddit.auth.repository;

import com.kapil.reddit.auth.domain.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    Optional<PasswordResetToken> findByToken(String token);

    Optional<PasswordResetToken> findTopByUser_IdOrderByIdDesc(Long userId);

    void deleteByUser_Id(Long userId);
}
