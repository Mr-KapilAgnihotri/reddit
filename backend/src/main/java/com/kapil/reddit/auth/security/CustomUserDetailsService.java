package com.kapil.reddit.auth.security;

import com.kapil.reddit.user.domain.User;
import com.kapil.reddit.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    /**
     * Loads user by email and maps the domain User to Spring Security UserDetails.
     *
     * <p>The {@code accountEnabled} flag maps to {@code user.isActive}. When an
     * admin bans a user (sets isActive=false), Spring Security will refuse authentication
     * or any filter-chain request for that principal — effectively blocking them without
     * needing a Redis token blocklist.
     *
     * <p>Short access-token TTL (15 min) means any already-issued token expires within
     * one TTL window. For immediate revocation, a jti-based Redis blocklist can be added
     * as a pre-auth filter that rejects tokens before reaching this service.
     */
    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        return org.springframework.security.core.userdetails.User
                .withUsername(user.getEmail())
                .password(user.getPasswordHash())
                .authorities(
                        user.getRoles()
                                .stream()
                                .map(role -> "ROLE_" + role.getName())
                                .toArray(String[]::new)
                )
                // isActive=false → banned user → Spring Security returns 401/403 immediately
                .accountExpired(false)
                .accountLocked(!Boolean.TRUE.equals(user.getIsActive()))
                .credentialsExpired(false)
                .disabled(false)
                .build();
    }
}

