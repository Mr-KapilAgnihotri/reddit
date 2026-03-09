package com.kapil.reddit.community.domain;

import com.kapil.reddit.user.domain.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "community_moderators")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CommunityModerator {

    @EmbeddedId
    private CommunityModeratorId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("communityId")
    private Community community;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("userId")
    private User user;

    private Instant assignedAt;

}
