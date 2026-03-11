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

    // Create Post
    @PostMapping
    public PostResponse createPost(
            Authentication authentication,
            @RequestBody CreatePostRequest request
    ) {

        return postService.createPost(authentication.getName(), request);
    }

    // Get Single Post
    @GetMapping("/{id}")
    public PostResponse getPost(@PathVariable Long id) {

        return postService.getPost(id);
    }

    // Get Posts by Community
    @GetMapping("/community/{communityName}")
    public List<PostResponse> getCommunityPosts(
            @PathVariable String communityName
    ) {

        return postService.getCommunityPosts(communityName);
    }

    // Delete Post
    @DeleteMapping("/{id}")
    public void deletePost(
            @PathVariable Long id,
            Authentication authentication
    ) {

        postService.deletePost(id, authentication.getName());
    }

}