package com.kapil.reddit.user.controller;

import com.kapil.reddit.user.dto.CreateUserRequest;
import com.kapil.reddit.user.dto.UserResponse;
import com.kapil.reddit.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;


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
