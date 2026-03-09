package com.kapil.reddit.community.domain;

import jakarta.persistence.Embeddable;
import lombok.*;

import java.io.Serializable;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CommunityMemberId implements Serializable {
    private Long userId;

    private Long communityId;
}
