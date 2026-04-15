package com.kapil.reddit.comment.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;

import java.time.Instant;
import java.util.List;

@Getter
@Builder
@Jacksonized
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CommentResponse {

    private Long id;

    /** displayText — or "[deleted]" for soft-deleted comments */
    private String text;

    /** Username of the comment author, or "[deleted]" */
    private String author;

    private Long score;
    private Long upvotes;
    private Long downvotes;

    private Instant createdAt;

    /**
     * Current user's vote on this comment.
     * Default is 0 (no vote / unauthenticated). Never null — critical for
     * frontend rendering consistency.
     */
    @Builder.Default
    private Short userVote = 0;

    private Boolean isDeleted;

    /** Nested child comments sorted by createdAt ASC. Empty list if leaf. */
    private List<CommentResponse> children;
}
