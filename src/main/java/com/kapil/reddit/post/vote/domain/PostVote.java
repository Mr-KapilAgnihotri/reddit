package com.kapil.reddit.post.vote.domain;

import jakarta.persistence.*;

import java.time.Instant;
import lombok.*;

@Entity
@Table(name = "post_votes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@IdClass(PostVoteId.class)
public class PostVote {

    @Id
    private Long userId;

    @Id
    private Long postId;

    private Short value; // +1 or -1

    private Instant createdAt;
}