package com.kapil.reddit.post.vote.controller;

import com.kapil.reddit.post.vote.dto.VoteRequest;
import com.kapil.reddit.post.vote.repository.PostVoteRepository;
import com.kapil.reddit.post.vote.service.PostVoteService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

public class PostVoteController {
    private PostVoteService postVoteService;

    @PostMapping("/{id}/vote")
    public void vote(
            @PathVariable Long id,
            @RequestBody VoteRequest request,
            Authentication authentication
    ) {
        postVoteService.vote(id, authentication.getName(), request.toValue());
    }

}
