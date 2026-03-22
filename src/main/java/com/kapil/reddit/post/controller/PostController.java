package com.kapil.reddit.post.controller;

import com.kapil.reddit.post.dto.CreatePostRequest;
import com.kapil.reddit.post.dto.PostResponse;
import com.kapil.reddit.post.dto.UpdatePostRequest;
import com.kapil.reddit.post.service.PostService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
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
    public Page<PostResponse> globalFeed(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return postService.getGlobalPosts(page, size);
    }

    // Home feed (joined communities)
    @GetMapping("/home")
    public Page<PostResponse> homeFeed(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return postService.getHomeFeed(authentication.getName(), page, size);
    }

    @GetMapping("/user/{username}")
    public Page<PostResponse> getUserPosts(
            @PathVariable String username,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return postService.getUserPosts(username, page, size);
    }



    @PutMapping("/{id}")
    public PostResponse updatePost(
            @PathVariable Long id,
            Authentication authentication,
            @RequestBody UpdatePostRequest request
    ) {
        return postService.updatePost(id, authentication.getName(), request);
    }

    @GetMapping("/community/{name}")
    public Page<PostResponse> getCommunityPosts(
            @PathVariable String name,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return postService.getCommunityPosts(name, page, size);
    }
}
