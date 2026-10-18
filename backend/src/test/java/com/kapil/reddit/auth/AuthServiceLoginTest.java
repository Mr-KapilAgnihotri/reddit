package com.kapil.reddit.auth;

import com.kapil.reddit.auth.domain.RefreshToken;
import com.kapil.reddit.auth.dto.LoginRequest;
import com.kapil.reddit.auth.dto.LoginResponse;
import com.kapil.reddit.auth.repository.RefreshTokenRepository;
import com.kapil.reddit.auth.security.JwtService;
import com.kapil.reddit.auth.service.AuthService;
import com.kapil.reddit.auth.service.RefreshTokenService;
import com.kapil.reddit.common.exception.BusinessException;
import com.kapil.reddit.user.domain.User;
import com.kapil.reddit.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link AuthService#login(LoginRequest)}.
 *
 * Pure Mockito; no Spring context required.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceLoginTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtService jwtService;
    @Mock private RefreshTokenService refreshTokenService;
    @Mock private RefreshTokenRepository refreshTokenRepository;

    @InjectMocks
    private AuthService authService;

    private User user;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .id(1L)
                .username("alice")
                .email("alice@test.com")
                .passwordHash("hashed")
                .isActive(true)
                .isVerified(true)
                .build();
    }

    private LoginRequest request(String email, String password) {
        LoginRequest r = new LoginRequest();
        r.setEmail(email);
        r.setPassword(password);
        return r;
    }

    @Test
    void login_verifiedActiveUser_returnsTokens() {
        when(userRepository.findByEmail("alice@test.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("secret", "hashed")).thenReturn(true);
        when(jwtService.generateAccessToken(eq("alice@test.com"), any())).thenReturn("access");
        when(refreshTokenService.createRefreshToken(user))
                .thenReturn(RefreshToken.builder().token("refresh").build());

        LoginResponse response = authService.login(request("alice@test.com", "secret"));

        assertThat(response.getAccess_token()).isEqualTo("access");
        assertThat(response.getRefresh_token()).isEqualTo("refresh");
        assertThat(response.getToken_type()).isEqualTo("Bearer");
    }

    @Test
    void login_unknownEmail_rejectedWithGenericMessage() {
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(request("nobody@test.com", "secret")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Invalid email or password");
    }

    @Test
    void login_wrongPassword_rejectedWithGenericMessage() {
        when(userRepository.findByEmail("alice@test.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "hashed")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(request("alice@test.com", "wrong")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Invalid email or password");
        verifyNoInteractions(refreshTokenService);
    }

    @Test
    void login_unverifiedEmail_rejected() {
        user.setIsVerified(false);
        when(userRepository.findByEmail("alice@test.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("secret", "hashed")).thenReturn(true);

        assertThatThrownBy(() -> authService.login(request("alice@test.com", "secret")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Email not verified");
        verifyNoInteractions(jwtService, refreshTokenService);
    }

    @Test
    void login_bannedUser_rejected() {
        user.setIsActive(false);
        when(userRepository.findByEmail("alice@test.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("secret", "hashed")).thenReturn(true);

        assertThatThrownBy(() -> authService.login(request("alice@test.com", "secret")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Account is banned");
        verifyNoInteractions(jwtService, refreshTokenService);
    }
}
