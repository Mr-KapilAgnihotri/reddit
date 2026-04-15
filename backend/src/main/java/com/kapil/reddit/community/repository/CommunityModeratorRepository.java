package com.kapil.reddit.community.repository;

import com.kapil.reddit.community.domain.CommunityModerator;
import com.kapil.reddit.community.domain.CommunityModeratorId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface CommunityModeratorRepository extends JpaRepository<CommunityModerator, CommunityModeratorId> {

    boolean existsByUserIdAndCommunityId(Long userId, Long communityId);

    @Query("""
SELECT cm FROM CommunityModerator cm
JOIN cm.community c
WHERE cm.user.id = :userId
AND c.isDeleted = false
""")
    List<CommunityModerator> findActiveModerations(Long userId);
    void deleteAllByCommunityId(Long communityId);
    List<CommunityModerator> findByUserId(Long userId);
}
