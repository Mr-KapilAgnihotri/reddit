package com.kapil.reddit.post.controller;

import com.kapil.reddit.post.dto.PostResponse;
import com.kapil.reddit.post.service.SavedPostService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class SavedPostController {

    private final SavedPostService savedPostService;

    // ─── POST /api/posts/{postId}/save ────────────────────────────────────────
    /** Save a post. Returns 409 if already saved. */
    @PostMapping("/api/posts/{postId}/save")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void savePost(@PathVariable Long postId, Authentication auth) {
        savedPostService.savePost(auth.getName(), postId);
    }

    // ─── DELETE /api/posts/{postId}/save ──────────────────────────────────────
    /** Unsave a post. Returns 400 if not currently saved. */
    @DeleteMapping("/api/posts/{postId}/save")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unsavePost(@PathVariable Long postId, Authentication auth) {
        savedPostService.unsavePost(auth.getName(), postId);
    }

    // ─── GET /api/users/me/saved-posts ────────────────────────────────────────
    /** Paginated list of the authenticated user's saved posts, newest-saved first. */
    @GetMapping("/api/users/me/saved-posts")
    public Page<PostResponse> getSavedPosts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication auth
    ) {
        return savedPostService.getSavedPosts(auth.getName(), page, size);
    }
}
