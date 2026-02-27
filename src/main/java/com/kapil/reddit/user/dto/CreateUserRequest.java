package com.kapil.reddit.user.dto;

import lombok.Data;

@Data
public class CreateUserRequest {

    private String username;
    private String email;
    private String password;
}
