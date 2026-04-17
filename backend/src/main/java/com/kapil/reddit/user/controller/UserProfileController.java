package com.kapil.reddit.user.controller;

import com.kapil.reddit.user.dto.UserProfileRequest;
import com.kapil.reddit.user.dto.UserProfileResponse;
import com.kapil.reddit.user.service.UserProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class UserProfileController {

    private final UserProfileService profileService;

    // ─── GET /api/users/{username}/profile ───────────────────────────────────
    /** Public profile — no auth required. */
    @GetMapping("/api/users/{username}/profile")
    public UserProfileResponse getPublicProfile(@PathVariable String username) {
        return profileService.getPublicProfile(username);
    }

    // ─── GET /api/users/me/profile ────────────────────────────────────────────
    /** Private view of own profile (includes is_verified). */
    @GetMapping("/api/users/me/profile")
    public UserProfileResponse getMyProfile(Authentication auth) {
        return profileService.getMyProfile(auth.getName());
    }

    // ─── PUT /api/users/me/profile ────────────────────────────────────────────
    /** Updates bio / avatar / displayName. PATCH semantics — null fields ignored. */
    @PutMapping("/api/users/me/profile")
    public UserProfileResponse updateMyProfile(
            @Valid @RequestBody UserProfileRequest request,
            Authentication auth
    ) {
        return profileService.updateMyProfile(auth.getName(), request);
    }
}
