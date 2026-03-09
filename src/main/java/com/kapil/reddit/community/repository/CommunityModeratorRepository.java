package com.kapil.reddit.community.repository;

import com.kapil.reddit.community.domain.CommunityModerator;
import com.kapil.reddit.community.domain.CommunityModeratorId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommunityModeratorRepository extends JpaRepository<CommunityModerator, CommunityModeratorId> {

    boolean existsByUserIdAndCommunityId(Long userId, Long communityId);

}
