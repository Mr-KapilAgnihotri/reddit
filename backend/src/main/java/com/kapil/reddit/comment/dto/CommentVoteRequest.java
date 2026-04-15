package com.kapil.reddit.comment.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CommentVoteRequest {

    private Integer value; // 1 for upvote, -1 for downvote

    /**
     * Validates and converts to Short.
     * Rejects any value that is not strictly +1 or -1.
     */
    public short toValue() {
        if (value == null || (value != 1 && value != -1)) {
            throw new IllegalArgumentException("Vote value must be 1 or -1");
        }
        return value.shortValue();
    }
}
