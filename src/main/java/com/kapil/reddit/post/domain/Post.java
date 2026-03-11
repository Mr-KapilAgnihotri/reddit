package com.kapil.reddit.post.domain;

import com.kapil.reddit.community.domain.Community;
import com.kapil.reddit.user.domain.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "posts")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Post {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "community_id")
    private Community community;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id")
    private User author;

    private String title;

    @Column(name = "original_text")
    private String originalText;

    @Column(name = "display_text")
    private String displayText;

    private Long upvotes;
    private Long downvotes;
    private Long score;
    private Long commentCount;
    private Long viewCount;

    private Double hotScore;

    private Boolean isDeleted;

    private Instant createdAt;
    private Instant updatedAt;
}
