package com.kapil.reddit.auth.repository;

import com.kapil.reddit.auth.domain.RefreshToken;
import com.kapil.reddit.user.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByToken(String token);
    void deleteByToken(String token);
    void deleteByUser(User user);
    List<RefreshToken> findAllByUser(User user);
}
