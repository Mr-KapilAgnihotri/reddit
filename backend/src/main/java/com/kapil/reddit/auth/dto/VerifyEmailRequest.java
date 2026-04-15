package com.kapil.reddit.auth.dto;

import lombok.Data;

@Data
public class VerifyEmailRequest {

    /** Used when token is sent in JSON body instead of a query parameter. */
    private String token;
}
