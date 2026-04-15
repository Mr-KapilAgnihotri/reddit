package com.kapil.reddit.auth.controller;

import com.kapil.reddit.auth.dto.ForgotPasswordRequest;
import com.kapil.reddit.auth.dto.LoginRequest;
import com.kapil.reddit.auth.dto.LoginResponse;
import com.kapil.reddit.auth.dto.RefreshTokenRequest;
import com.kapil.reddit.auth.dto.ResetPasswordRequest;
import com.kapil.reddit.auth.dto.VerifyEmailRequest;
import com.kapil.reddit.auth.service.AuthService;
import com.kapil.reddit.auth.service.EmailVerificationService;
import com.kapil.reddit.auth.service.PasswordResetService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final EmailVerificationService emailVerificationService;
    private final PasswordResetService passwordResetService;

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request){
        return authService.login(request);
    }

    @PostMapping("/refresh")
    public ResponseEntity<LoginResponse> refresh(
            @RequestBody RefreshTokenRequest request) {

        LoginResponse response =
                authService.refresh(request.getRefresh_token());

        return ResponseEntity.ok(response);
    }


    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @RequestBody RefreshTokenRequest request) {

        authService.logout(request.getRefresh_token());

        return ResponseEntity.noContent().build();
    }

    @PostMapping("/logout-all")
    public ResponseEntity<Void> logoutAll(Authentication authentication) {

        String email = authentication.getName();

        authService.logoutAll(email);

        return ResponseEntity.noContent().build();
    }

    @GetMapping("/verify")
    public ResponseEntity<Map<String, String>> verifyEmailGet(@RequestParam String token) {
        emailVerificationService.verifyEmail(token.trim());
        return ResponseEntity.ok(Map.of("message", "Email verified"));
    }

    @PostMapping("/verify")
    public ResponseEntity<Map<String, String>> verifyEmailPost(
            @RequestParam(required = false) String token,
            @RequestBody(required = false) VerifyEmailRequest body) {
        emailVerificationService.verifyEmail(resolveVerificationToken(token, body));
        return ResponseEntity.ok(Map.of("message", "Email verified"));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        passwordResetService.forgotPassword(request.getEmail());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        passwordResetService.resetPassword(request.getToken(), request.getNewPassword());
        return ResponseEntity.ok().build();
    }

    private static String resolveVerificationToken(String queryToken, VerifyEmailRequest body) {
        if (queryToken != null && !queryToken.isBlank()) {
            return queryToken.trim();
        }
        if (body != null && body.getToken() != null && !body.getToken().isBlank()) {
            return body.getToken().trim();
        }
        throw new IllegalArgumentException("Token is required");
    }

}
