package com.kapil.reddit.post.controller;

import com.kapil.reddit.post.dto.CreatePostRequest;
import com.kapil.reddit.post.dto.PostResponse;
import com.kapil.reddit.post.service.PostService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/posts")
@RequiredArgsConstructor
public class PostController {

    private final PostService postService;

    @PostMapping
    public PostResponse createPost(
            Authentication authentication,
            @RequestBody CreatePostRequest request
    ) {
        return postService.createPost(authentication.getName(), request);
    }

    @GetMapping("/{id}")
    public PostResponse getPost(@PathVariable Long id) {
        return postService.getPost(id);
    }

    @DeleteMapping("/{id}")
    public void deletePost(
            @PathVariable Long id,
            Authentication authentication
    ) {
        postService.deletePost(id, authentication.getName());
    }

    //  global feed
    @GetMapping("/global")
    public List<PostResponse> globalFeed() {
        return postService.getGlobalPosts();
    }

    // Home feed (joined communities)
    @GetMapping("/home")
    public List<PostResponse> homeFeed(Authentication authentication) {
        return postService.getHomeFeed(authentication.getName());
    }

    @GetMapping("/user/{username}")
    public List<PostResponse> getUserPosts(@PathVariable String username) {

        return postService.getUserPosts(username);
    }
}
