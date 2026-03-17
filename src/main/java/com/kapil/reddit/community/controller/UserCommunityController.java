package com.kapil.reddit.community.controller;

import com.kapil.reddit.community.dto.CommunityResponse;
import com.kapil.reddit.community.service.CommunityService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/users/me/communities")
@RequiredArgsConstructor
public class UserCommunityController {

    private final CommunityService communityService;

    @GetMapping("/created")
    public List<CommunityResponse> created(Authentication auth) {
        return communityService.getCommunitiesCreatedByUser(auth.getName());
    }

    @GetMapping("/joined")
    public List<CommunityResponse> joined(Authentication auth) {
        return communityService.getJoinedCommunities(auth.getName());
    }

    @GetMapping("/moderating")
    public List<CommunityResponse> moderating(Authentication auth) {
        return communityService.getModeratedCommunities(auth.getName());
    }
}