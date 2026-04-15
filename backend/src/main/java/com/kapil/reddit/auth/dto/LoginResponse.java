package com.kapil.reddit.auth.dto;

import lombok.Builder;
import lombok.Data;
import lombok.Getter;

@Data
@Builder
@Getter
public class LoginResponse {

    private String access_token;
    private String refresh_token;
    private String token_type;
}
