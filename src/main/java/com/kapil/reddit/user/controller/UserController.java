package com.kapil.reddit.user.controller;

import com.kapil.reddit.user.dto.CreateUserRequest;
import com.kapil.reddit.user.dto.UpdateUserRolesRequest;
import com.kapil.reddit.user.dto.UserResponse;
import com.kapil.reddit.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;

    @PostMapping
    public UserResponse createUser(@Valid @RequestBody CreateUserRequest request) {
        return userService.createUser(request);
    }

    @PatchMapping("/{id}/roles")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> updateUserRoles(
            @PathVariable Long id,
            @RequestBody UpdateUserRolesRequest request
    ) {
        userService.updateUserRoles(id, request.getRoles());
        return ResponseEntity.noContent().build();
    }


}
