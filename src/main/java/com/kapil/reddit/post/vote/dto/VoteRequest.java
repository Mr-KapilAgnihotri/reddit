package com.kapil.reddit.post.vote.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class VoteRequest {

    private Integer value; // 1 for upvote, -1 for downvote

    public short toValue() {
        if (value == null || (value != 1 && value != -1)) {
            throw new IllegalArgumentException("Vote value must be 1 or -1");
        }
        return value.shortValue();
    }
}