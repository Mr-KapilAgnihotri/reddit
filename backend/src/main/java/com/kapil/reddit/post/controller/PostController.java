package com.kapil.reddit.post.controller;

import com.kapil.reddit.post.dto.CreatePostRequest;
import com.kapil.reddit.post.dto.PostResponse;
import com.kapil.reddit.post.dto.UpdatePostRequest;
import com.kapil.reddit.post.service.PostService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/posts")
@RequiredArgsConstructor
public class PostController {

    private final PostService postService;

    private String getEmail(Authentication authentication) {
        return authentication != null ? authentication.getName() : null;
    }

    @PostMapping
    public PostResponse createPost(
            Authentication authentication,
            @RequestBody CreatePostRequest request
    ) {
        return postService.createPost(getEmail(authentication), request);
    }

    @GetMapping("/{id}")
    public PostResponse getPost(
            @PathVariable Long id,
            Authentication authentication
    ) {
        return postService.getPost(id, getEmail(authentication));
    }

    @DeleteMapping("/{id}")
    public void deletePost(
            @PathVariable Long id,
            Authentication authentication
    ) {
        postService.deletePost(id, getEmail(authentication));
    }

    // global feed
    @GetMapping("/global")
    public Page<PostResponse> globalFeed(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "new") String sort
    ) {
        return postService.getGlobalPosts(getEmail(authentication), page, size, sort);
    }

    // Home feed (joined communities)
    @GetMapping("/home")
    public Page<PostResponse> homeFeed(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "new") String sort
    ) {
        return postService.getHomeFeed(getEmail(authentication), page, size, sort);
    }

    @GetMapping("/user/{username}")
    public Page<PostResponse> getUserPosts(
            Authentication authentication,
            @PathVariable String username,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "new") String sort
    ) {
        return postService.getUserPosts(getEmail(authentication), username, page, size, sort);
    }

    @PutMapping("/{id}")
    public PostResponse updatePost(
            @PathVariable Long id,
            Authentication authentication,
            @RequestBody UpdatePostRequest request
    ) {
        return postService.updatePost(id, getEmail(authentication), request);
    }

    @GetMapping("/community/{name}")
    public Page<PostResponse> getCommunityPosts(
            Authentication authentication,
            @PathVariable String name,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "new") String sort
    ) {
        return postService.getCommunityPosts(getEmail(authentication), name, page, size, sort);
    }

    /**
     * Personalized recommendation feed using PGVector cosine similarity.
     * Requires authentication. Falls back to global (newest) feed if the user
     * has no upvoted/saved posts with embeddings yet.
     */
    @GetMapping("/recommended")
    public Page<PostResponse> recommendedFeed(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return postService.getRecommendedFeed(authentication.getName(), page, size);
    }
}

