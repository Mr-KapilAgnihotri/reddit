package com.kapil.reddit.user.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class BanUserRequest {
    /** Human-readable reason for the ban — stored in users.ban_reason. */
    private String reason;
}
