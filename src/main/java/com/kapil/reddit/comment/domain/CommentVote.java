package com.kapil.reddit.comment.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "comment_votes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@IdClass(CommentVoteId.class)
public class CommentVote {

    @Id
    private Long userId;

    @Id
    private Long commentId;

    /** Strictly +1 (upvote) or -1 (downvote). Validated at service layer. */
    private Short value;

    private Instant createdAt;
}
