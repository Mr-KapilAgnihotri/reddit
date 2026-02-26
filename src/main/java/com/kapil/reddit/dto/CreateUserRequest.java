package com.kapil.reddit.dto;

import lombok.Data;

@Data
public class CreateUserRequest {

    private String username;
    private String email;
    private String password;
}
