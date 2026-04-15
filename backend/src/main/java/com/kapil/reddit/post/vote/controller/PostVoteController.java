package com.kapil.reddit.post.vote.controller;

import com.kapil.reddit.post.dto.PostResponse;
import com.kapil.reddit.post.vote.dto.VoteRequest;
import com.kapil.reddit.post.vote.service.PostVoteService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/posts")
@RequiredArgsConstructor
public class PostVoteController {

    private final PostVoteService postVoteService;

    @PostMapping("/{id}/vote")
    public PostResponse vote(
            @PathVariable Long id,
            @RequestBody VoteRequest request,
            Authentication authentication
    ) {
        return postVoteService.vote(id, authentication.getName(), request.toValue());
    }
}
