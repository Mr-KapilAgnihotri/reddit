package com.kapil.reddit.post.vote.dto;

public class VoteRequest {

    private String voteType; // "UPVOTE" or "DOWNVOTE"

    public short toValue() {
        if ("UPVOTE".equalsIgnoreCase(voteType)) return 1;
        if ("DOWNVOTE".equalsIgnoreCase(voteType)) return -1;
        throw new IllegalArgumentException("Invalid vote type");
    }
}