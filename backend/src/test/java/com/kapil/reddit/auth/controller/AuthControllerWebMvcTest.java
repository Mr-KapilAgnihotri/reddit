package com.kapil.reddit.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kapil.reddit.auth.dto.ForgotPasswordRequest;
import com.kapil.reddit.auth.security.JwtAuthenticationFilter;
import com.kapil.reddit.auth.service.AuthService;
import com.kapil.reddit.auth.service.EmailVerificationService;
import com.kapil.reddit.auth.service.PasswordResetService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AuthService authService;

    @MockBean
    private EmailVerificationService emailVerificationService;

    @MockBean
    private PasswordResetService passwordResetService;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @Test
    void postVerify_missingToken_returns400() throws Exception {
        mockMvc.perform(post("/api/auth/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
        verify(emailVerificationService, never()).verifyEmail(anyString());
    }

    @Test
    void getVerify_invokesService() throws Exception {
        mockMvc.perform(get("/api/auth/verify").param("token", "abc"))
                .andExpect(status().isOk());
        verify(emailVerificationService).verifyEmail("abc");
    }

    @Test
    void forgotPassword_returns200() throws Exception {
        ForgotPasswordRequest body = new ForgotPasswordRequest();
        body.setEmail("a@b.com");
        mockMvc.perform(post("/api/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk());
        verify(passwordResetService).forgotPassword("a@b.com");
    }
}
