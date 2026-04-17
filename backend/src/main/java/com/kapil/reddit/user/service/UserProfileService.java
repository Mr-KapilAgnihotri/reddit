package com.kapil.reddit.user.service;

import com.kapil.reddit.common.exception.BusinessException;
import com.kapil.reddit.common.exception.ResourceNotFoundException;
import com.kapil.reddit.user.domain.User;
import com.kapil.reddit.user.domain.UserProfile;
import com.kapil.reddit.user.dto.UserProfileRequest;
import com.kapil.reddit.user.dto.UserProfileResponse;
import com.kapil.reddit.user.repository.UserProfileRepository;
import com.kapil.reddit.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserProfileService {

    private final UserRepository       userRepository;
    private final UserProfileRepository profileRepository;

    // ─── PUBLIC PROFILE ───────────────────────────────────────────────────────

    /** Fetches the public profile for any username. 404 if user doesn't exist. */
    @Transactional(readOnly = true)
    public UserProfileResponse getPublicProfile(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + username));

        if (Boolean.TRUE.equals(user.getIsDeleted())) {
            throw new ResourceNotFoundException("User not found: " + username);
        }

        UserProfile profile = profileRepository.findByUserId(user.getId())
                .orElse(null);  // Profile row may not exist yet → defaults

        return buildResponse(user, profile);
    }

    // ─── MY PROFILE (GET) ─────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public UserProfileResponse getMyProfile(String email) {
        User user = getUser(email);
        UserProfile profile = profileRepository.findByUserId(user.getId()).orElse(null);
        return buildResponse(user, profile);
    }

    // ─── MY PROFILE (UPDATE) ─────────────────────────────────────────────────

    /**
     * PATCH semantics — only non-null fields in the request are applied.
     * Profile row is created lazily on first update.
     */
    @Transactional
    public UserProfileResponse updateMyProfile(String email, UserProfileRequest request) {
        User user = getUser(email);

        UserProfile profile = profileRepository.findByUserId(user.getId())
                .orElseGet(() -> UserProfile.builder()
                        .userId(user.getId())
                        .user(user)
                        .build());

        if (request.getDisplayName() != null) {
            profile.setDisplayName(request.getDisplayName());
        }
        if (request.getBio() != null) {
            profile.setBio(request.getBio());
        }
        if (request.getAvatarUrl() != null) {
            profile.setAvatarUrl(request.getAvatarUrl());
        }

        profileRepository.save(profile);

        return buildResponse(user, profile);
    }

    // ─── HELPERS ──────────────────────────────────────────────────────────────

    private User getUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException("User not found"));
    }

    private UserProfileResponse buildResponse(User user, UserProfile profile) {
        return UserProfileResponse.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .displayName(profile != null ? profile.getDisplayName() : null)
                .bio(profile != null ? profile.getBio() : null)
                .avatarUrl(profile != null ? profile.getAvatarUrl() : null)
                .isVerified(Boolean.TRUE.equals(user.getIsVerified()))
                .build();
    }
}
