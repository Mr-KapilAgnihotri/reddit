package com.kapil.reddit.comment.domain;

import com.kapil.reddit.post.domain.Post;
import com.kapil.reddit.user.domain.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "comments")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Comment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id", nullable = false)
    private Post post;

    /**
     * Null for root-level comments.
     * Cross-post nesting is prevented at the service layer by validating
     * that parentComment.post.id == request.postId.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_comment_id")
    private Comment parentComment;

    /**
     * LAZY — never load children via JPA; tree is built in-memory from a
     * single flat query to avoid N+1 problems.
     */
    @OneToMany(mappedBy = "parentComment", fetch = FetchType.LAZY)
    @Builder.Default
    private List<Comment> children = new ArrayList<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id", nullable = false)
    private User author;

    /** Raw user input — never modified after creation. Reserved for ML pipeline. */
    @Column(name = "original_text")
    private String originalText;

    /**
     * Text shown to users. Currently equals originalText.
     * ML pipeline will later update this field (mask profanity, etc.)
     * without Kafka wiring at this stage.
     */
    @Column(name = "display_text")
    private String displayText;

    private Long upvotes;
    private Long downvotes;
    private Long score;

    /** Soft-delete flag — never physically remove comments (children must stay). */
    @Column(name = "is_deleted")
    private Boolean isDeleted;

    /**
     * ML moderation flag. Set to true once the ML pipeline has processed this comment.
     * Added via V9 migration.
     */
    @Column(name = "is_moderated")
    private Boolean isModerated;

    private Instant createdAt;
}
