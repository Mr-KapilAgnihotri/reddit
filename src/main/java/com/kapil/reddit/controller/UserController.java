package com.kapil.reddit.controller;

import com.kapil.reddit.dto.CreateUserRequest;
import com.kapil.reddit.dto.UserResponse;
import com.kapil.reddit.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import com.kapil.reddit.domain.User;


@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;

    @PostMapping
    public UserResponse createUser(@RequestBody CreateUserRequest request) {
        return userService.createUser(request);
    }
}
