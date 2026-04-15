package com.kapil.reddit.community.domain;

import jakarta.persistence.Embeddable;
import lombok.*;

import java.io.Serializable;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CommunityModeratorId implements Serializable {

    private Long communityId;

    private Long userId;
}