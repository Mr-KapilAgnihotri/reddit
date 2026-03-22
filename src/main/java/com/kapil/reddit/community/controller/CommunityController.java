package com.kapil.reddit.community.controller;

import com.kapil.reddit.community.dto.CommunityResponse;
import com.kapil.reddit.community.dto.CreateCommunityRequest;
import com.kapil.reddit.community.service.CommunityService;
import com.kapil.reddit.post.dto.PostResponse;
import com.kapil.reddit.post.service.PostService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/communities")
@RequiredArgsConstructor
public class CommunityController {

    private final CommunityService communityService;
    private final PostService postService;

    @PostMapping
    public CommunityResponse createCommunity(
            Authentication authentication,
            @RequestBody CreateCommunityRequest request
    ) {
        return communityService.createCommunity(authentication.getName(), request);
    }

    @GetMapping
    public List<CommunityResponse> listCommunities() {
        return communityService.listCommunities();
    }

    @GetMapping("/{name}")
    public CommunityResponse getCommunity(@PathVariable String name) {
        return communityService.getCommunity(name);
    }

    @DeleteMapping("/{id}")
    public void deleteCommunity(
            @PathVariable Long id,
            Authentication authentication
    ) {
        communityService.deleteCommunity(id, authentication.getName());
    }

    @PostMapping("/{id}/join")
    public void joinCommunity(
            @PathVariable Long id,
            Authentication authentication
    ) {
        communityService.joinCommunity(id, authentication.getName());
    }

    @PostMapping("/{id}/leave")
    public void leaveCommunity(
            @PathVariable Long id,
            Authentication authentication
    ) {
        communityService.leaveCommunity(id, authentication.getName());
    }

    @PostMapping("/{id}/moderators/{userId}")
    public void addModerator(
            @PathVariable Long id,
            @PathVariable Long userId,
            Authentication authentication
    ) {
        communityService.addModerator(id, userId, authentication.getName());
    }

    @DeleteMapping("/{id}/moderators/{userId}")
    public void removeModerator(
            @PathVariable Long id,
            @PathVariable Long userId,
            Authentication authentication
    ) {
        communityService.removeModerator(id, userId, authentication.getName());
    }

    @GetMapping("/{name}/posts")
    public Page<PostResponse> getCommunityPosts(
            @PathVariable String name,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return postService.getCommunityPosts(name, page, size);
    }

    @GetMapping("/search")
    public List<CommunityResponse> searchCommunities(@RequestParam String query) {
        return communityService.searchCommunities(query);
    }
}
