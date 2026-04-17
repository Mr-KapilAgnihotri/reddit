package com.kapil.reddit.user.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;

@Getter
@Builder
@Jacksonized
public class UserProfileResponse {

    private Long userId;
    private String username;
    private String displayName;
    private String bio;
    private String avatarUrl;
    private boolean isVerified;
}
