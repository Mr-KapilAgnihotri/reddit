package com.kapil.reddit.comment.domain;

import java.io.Serializable;
import java.util.Objects;

public class CommentVoteId implements Serializable {

    private Long userId;
    private Long commentId;

    public CommentVoteId() {}

    public CommentVoteId(Long userId, Long commentId) {
        this.userId = userId;
        this.commentId = commentId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CommentVoteId)) return false;
        CommentVoteId that = (CommentVoteId) o;
        return Objects.equals(userId, that.userId) &&
                Objects.equals(commentId, that.commentId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, commentId);
    }
}
