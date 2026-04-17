package com.kapil.reddit.common.admin;

import com.kapil.reddit.post.dto.AdminModerationRequest;
import com.kapil.reddit.user.dto.BanUserRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Admin-only moderation endpoints.
 * All methods are guarded by {@code @PreAuthorize("hasRole('ADMIN')")} — Spring
 * Security method-level security rejects non-admin callers with 403 before
 * the service layer is invoked.
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final AdminService adminService;

    // ─── Posts ────────────────────────────────────────────────────────────────

    /** Force soft-delete any post regardless of author. */
    @DeleteMapping("/posts/{postId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletePost(@PathVariable Long postId) {
        adminService.adminDeletePost(postId);
    }

    /** Override the ML moderation status (and optionally display text) of a post. */
    @PatchMapping("/posts/{postId}/moderation")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void overridePostModeration(
            @PathVariable Long postId,
            @RequestBody AdminModerationRequest request
    ) {
        adminService.overridePostModeration(postId, request);
    }

    // ─── Comments ─────────────────────────────────────────────────────────────

    /** Force soft-delete any comment regardless of author. */
    @DeleteMapping("/comments/{commentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteComment(@PathVariable Long commentId) {
        adminService.adminDeleteComment(commentId);
    }

    /** Override the ML moderation status of a comment. */
    @PatchMapping("/comments/{commentId}/moderation")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void overrideCommentModeration(
            @PathVariable Long commentId,
            @RequestBody AdminModerationRequest request
    ) {
        adminService.overrideCommentModeration(commentId, request);
    }

    // ─── Users ────────────────────────────────────────────────────────────────

    /**
     * Ban a user (set isActive=false).
     * Their existing JWT access tokens are valid for up to 15 minutes (short TTL window).
     * The CustomUserDetailsService checks isActive on each request, so they're effectively
     * blocked as soon as their next request hits the filter chain after the DB update.
     */
    @PatchMapping("/users/{userId}/ban")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void banUser(
            @PathVariable Long userId,
            @RequestBody(required = false) BanUserRequest request
    ) {
        adminService.banUser(userId, request);
    }

    /** Restore a banned user (set isActive=true, clear ban_reason). */
    @PatchMapping("/users/{userId}/unban")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unbanUser(@PathVariable Long userId) {
        adminService.unbanUser(userId);
    }
}
