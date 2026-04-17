package com.kapil.reddit.post.domain;

import com.kapil.reddit.user.domain.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * Represents a user's saved post (bookmark).
 * Maps to the {@code saved_posts} table (already created in V1 migration).
 */
@Entity
@Table(name = "saved_posts")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SavedPost {

    @EmbeddedId
    private SavedPostId id;

    /**
     * Joined to fetch the full Post and its associations in a single query — avoids N+1.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("postId")
    @JoinColumn(name = "post_id")
    private Post post;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("userId")
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "saved_at")
    private Instant savedAt;
}
