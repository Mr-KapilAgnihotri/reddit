package com.kapil.reddit.dto;

import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@Builder
public class UserResponse {
    private Long id;
    private String username;
    private String email;
    private String role;
    private Boolean isActive;
    private OffsetDateTime createdAt;
}
