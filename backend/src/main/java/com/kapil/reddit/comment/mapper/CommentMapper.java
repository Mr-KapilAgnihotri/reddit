package com.kapil.reddit.comment.mapper;

import com.kapil.reddit.comment.domain.Comment;
import com.kapil.reddit.comment.dto.CommentResponse;

import java.util.List;

public class CommentMapper {

    private CommentMapper() {}

    /**
     * Converts a Comment entity to a CommentResponse.
     *
     * @param comment   the comment entity (author must be loaded)
     * @param userVote  the current user's vote value (0 if unauthenticated or no vote)
     * @param children  pre-built list of child CommentResponses (sorted, recursive)
     */
    public static CommentResponse toResponse(Comment comment, Short userVote, List<CommentResponse> children) {
        boolean deleted = Boolean.TRUE.equals(comment.getIsDeleted());

        return CommentResponse.builder()
                .id(comment.getId())
                // Reddit-style: show placeholder text for deleted comments; children stay visible
                .text(deleted ? "[deleted]" : comment.getDisplayText())
                .author(deleted ? "[deleted]" : comment.getAuthor().getUsername())
                .score(comment.getScore())
                .upvotes(comment.getUpvotes())
                .downvotes(comment.getDownvotes())
                .createdAt(comment.getCreatedAt())
                .userVote(userVote != null ? userVote : 0)
                .isDeleted(deleted)
                .children(children)
                .build();
    }
}
