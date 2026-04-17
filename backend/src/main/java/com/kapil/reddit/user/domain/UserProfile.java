package com.kapil.reddit.user.domain;

import jakarta.persistence.*;
import lombok.*;

/**
 * One-to-one extension of {@link User} for public-facing profile data.
 * Maps to the {@code user_profiles} table (created in V1 migration).
 *
 * Uses userId as the primary key (shared PK pattern — no surrogate key needed).
 */
@Entity
@Table(name = "user_profiles")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProfile {

    /** Matches users.id — both PK and FK. */
    @Id
    @Column(name = "user_id")
    private Long userId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "display_name", length = 50)
    private String displayName;

    @Column(columnDefinition = "TEXT")
    private String bio;

    @Column(name = "avatar_url", columnDefinition = "TEXT")
    private String avatarUrl;
}
