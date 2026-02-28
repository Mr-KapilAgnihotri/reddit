package com.kapil.reddit.user.dto;

import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.List;

@Data
@Builder
public class UserResponse {
    private Long id;
    private String username;
    private String email;
    private List<String> roles;
    private Boolean isActive;
    private OffsetDateTime createdAt;
}
