package com.kapil.reddit.user.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.List;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserResponse {
    private Long id;
    private String username;
    private String email;
    private List<String> roles;
    private Boolean isActive;
    private OffsetDateTime createdAt;
    /** Present only when app.auth.expose-verification-token-on-register=true (not for production). */
    private String verificationToken;
}
