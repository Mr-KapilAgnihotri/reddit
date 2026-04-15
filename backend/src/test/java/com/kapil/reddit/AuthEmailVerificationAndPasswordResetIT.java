package com.kapil.reddit;

import com.kapil.reddit.auth.repository.EmailVerificationTokenRepository;
import com.kapil.reddit.support.AbstractIntegrationTest;
import com.kapil.reddit.user.dto.UserResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class AuthEmailVerificationAndPasswordResetIT extends AbstractIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @MockBean
    private JavaMailSender javaMailSender;

    @Autowired
    private EmailVerificationTokenRepository emailVerificationTokenRepository;

    private String baseUrl() {
        return "http://localhost:" + port;
    }

    private HttpHeaders jsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    @BeforeEach
    void resetMailInvocations() {
        clearInvocations(javaMailSender);
    }

    @Test
    void register_thenLoginFailsUntilVerified() {
        String email = "u-" + UUID.randomUUID() + "@test.com";
        String password = "Password12!";

        ResponseEntity<UserResponse> reg = restTemplate.postForEntity(
                baseUrl() + "/api/users",
                new HttpEntity<>(Map.of(
                        "username", "user-" + UUID.randomUUID().toString().substring(0, 8),
                        "email", email,
                        "password", password
                ), jsonHeaders()),
                UserResponse.class
        );
        assertThat(reg.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(javaMailSender, atLeastOnce()).send(any(SimpleMailMessage.class));

        ResponseEntity<String> login = restTemplate.postForEntity(
                baseUrl() + "/api/auth/login",
                new HttpEntity<>(Map.of("email", email, "password", password), jsonHeaders()),
                String.class
        );
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(login.getBody()).contains("Email not verified");
    }

    @Test
    void verifyEmail_thenLoginSucceeds_getAndPostVerify() {
        String email = "v-" + UUID.randomUUID() + "@test.com";
        String password = "Password12!";
        Long userId = registerUser(email, password);

        String token = emailVerificationTokenRepository.findTopByUser_IdOrderByIdDesc(userId)
                .orElseThrow()
                .getToken();

        ResponseEntity<String> verifyGet = restTemplate.getForEntity(
                baseUrl() + "/api/auth/verify?token=" + token,
                String.class
        );
        assertThat(verifyGet.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<String> login = restTemplate.postForEntity(
                baseUrl() + "/api/auth/login",
                new HttpEntity<>(Map.of("email", email, "password", password), jsonHeaders()),
                String.class
        );
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(login.getBody()).contains("access_token");

        // POST /verify with body on a fresh user
        String email2 = "v2-" + UUID.randomUUID() + "@test.com";
        Long userId2 = registerUser(email2, password);
        String token2 = emailVerificationTokenRepository.findTopByUser_IdOrderByIdDesc(userId2)
                .orElseThrow()
                .getToken();

        ResponseEntity<String> verifyPost = restTemplate.postForEntity(
                baseUrl() + "/api/auth/verify",
                new HttpEntity<>(Map.of("token", token2), jsonHeaders()),
                String.class
        );
        assertThat(verifyPost.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void verifyEmail_invalidToken_returns404() {
        ResponseEntity<String> res = restTemplate.getForEntity(
                baseUrl() + "/api/auth/verify?token=" + UUID.randomUUID(),
                String.class
        );
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void verifyEmail_expiredToken_returns400() {
        String email = "e-" + UUID.randomUUID() + "@test.com";
        String password = "Password12!";
        Long userId = registerUser(email, password);

        var entity = emailVerificationTokenRepository.findTopByUser_IdOrderByIdDesc(userId).orElseThrow();
        entity.setExpiresAt(Instant.now().minus(1, ChronoUnit.HOURS));
        emailVerificationTokenRepository.save(entity);

        ResponseEntity<String> res = restTemplate.getForEntity(
                baseUrl() + "/api/auth/verify?token=" + entity.getToken(),
                String.class
        );
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(res.getBody()).containsIgnoringCase("expired");
    }

    @Test
    void verifyEmail_reuseToken_returns404() {
        String email = "r-" + UUID.randomUUID() + "@test.com";
        String password = "Password12!";
        Long userId = registerUser(email, password);
        String token = emailVerificationTokenRepository.findTopByUser_IdOrderByIdDesc(userId)
                .orElseThrow()
                .getToken();

        assertThat(restTemplate.getForEntity(
                baseUrl() + "/api/auth/verify?token=" + token,
                String.class
        ).getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<String> second = restTemplate.getForEntity(
                baseUrl() + "/api/auth/verify?token=" + token,
                String.class
        );
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void forgotPassword_unknownEmail_succeedsWithoutSendingMail() {
        clearInvocations(javaMailSender);

        ResponseEntity<Void> res = restTemplate.postForEntity(
                baseUrl() + "/api/auth/forgot-password",
                new HttpEntity<>(Map.of("email", "nobody-" + UUID.randomUUID() + "@test.com"), jsonHeaders()),
                Void.class
        );
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(javaMailSender, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void forgotPassword_resetPassword_loginWithNewPassword_reuseTokenFails() {
        String email = "p-" + UUID.randomUUID() + "@test.com";
        String oldPassword = "Password12!";
        String newPassword = "Newpassw0rd!";
        Long userId = registerUser(email, oldPassword);
        String verifyToken = emailVerificationTokenRepository.findTopByUser_IdOrderByIdDesc(userId)
                .orElseThrow()
                .getToken();
        restTemplate.getForEntity(baseUrl() + "/api/auth/verify?token=" + verifyToken, String.class);

        clearInvocations(javaMailSender);

        ResponseEntity<Void> forgot = restTemplate.postForEntity(
                baseUrl() + "/api/auth/forgot-password",
                new HttpEntity<>(Map.of("email", email), jsonHeaders()),
                Void.class
        );
        assertThat(forgot.getStatusCode()).isEqualTo(HttpStatus.OK);

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(javaMailSender, atLeastOnce()).send(captor.capture());
        String body = captor.getValue().getText();
        String resetToken = body.substring(body.indexOf("token=") + "token=".length()).trim();
        int cut = resetToken.indexOf('\n');
        if (cut > 0) {
            resetToken = resetToken.substring(0, cut).trim();
        }

        ResponseEntity<Void> reset = restTemplate.postForEntity(
                baseUrl() + "/api/auth/reset-password",
                new HttpEntity<>(Map.of("token", resetToken, "newPassword", newPassword), jsonHeaders()),
                Void.class
        );
        assertThat(reset.getStatusCode()).isEqualTo(HttpStatus.OK);

        assertThat(restTemplate.postForEntity(
                baseUrl() + "/api/auth/login",
                new HttpEntity<>(Map.of("email", email, "password", newPassword), jsonHeaders()),
                String.class
        ).getStatusCode()).isEqualTo(HttpStatus.OK);

        assertThat(restTemplate.postForEntity(
                baseUrl() + "/api/auth/login",
                new HttpEntity<>(Map.of("email", email, "password", oldPassword), jsonHeaders()),
                String.class
        ).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        ResponseEntity<String> reuse = restTemplate.postForEntity(
                baseUrl() + "/api/auth/reset-password",
                new HttpEntity<>(Map.of("token", resetToken, "newPassword", "AnotherP8ss!"), jsonHeaders()),
                String.class
        );
        assertThat(reuse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void postVerify_missingToken_returns400() {
        ResponseEntity<String> res = restTemplate.exchange(
                baseUrl() + "/api/auth/verify",
                HttpMethod.POST,
                new HttpEntity<>("{}", jsonHeaders()),
                String.class
        );
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    private Long registerUser(String email, String password) {
        ResponseEntity<UserResponse> reg = restTemplate.postForEntity(
                baseUrl() + "/api/users",
                new HttpEntity<>(Map.of(
                        "username", "u-" + UUID.randomUUID().toString().substring(0, 8),
                        "email", email,
                        "password", password
                ), jsonHeaders()),
                UserResponse.class
        );
        assertThat(reg.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(reg.getBody()).isNotNull();
        return reg.getBody().getId();
    }
}
