package com.kapil.reddit.community.repository;

import com.kapil.reddit.community.domain.CommunityMember;
import com.kapil.reddit.community.domain.CommunityMemberId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CommunityMemberRepository extends JpaRepository<CommunityMember, CommunityMemberId> {

    boolean existsByUserIdAndCommunityId(Long userId, Long communityId);
    List<CommunityMember> findByUserId(Long userId);
    long countByCommunityId(Long communityId);
    void deleteAllByCommunityId(Long communityId);
}
