package com.kapil.reddit.community.service;

import com.kapil.reddit.common.exception.BusinessException;
import com.kapil.reddit.community.domain.*;
import com.kapil.reddit.community.dto.CommunityResponse;
import com.kapil.reddit.community.dto.CreateCommunityRequest;
import com.kapil.reddit.community.repository.CommunityMemberRepository;
import com.kapil.reddit.community.repository.CommunityModeratorRepository;
import com.kapil.reddit.community.repository.CommunityRepository;
import com.kapil.reddit.user.domain.User;
import com.kapil.reddit.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CommunityService {

    private final CommunityRepository communityRepository;
    private final UserRepository userRepository;
    private final CommunityMemberRepository communityMemberRepository;
    private final CommunityModeratorRepository communityModeratorRepository;

    public CommunityResponse createCommunity(String email, CreateCommunityRequest request) {

        if (communityRepository.existsByName(request.getName())) {
            throw new BusinessException("Community name already exists");
        }

        User creator = userRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException("User not found"));

        Community community = Community.builder()
                .name(request.getName())
                .description(request.getDescription())
                .createdBy(creator)
                .isPrivate(request.getIsPrivate() != null ? request.getIsPrivate() : false)
                .isDeleted(false)
                .createdAt(Instant.now())
                .build();

        Community saved = communityRepository.save(community);

        // add creator as member
        CommunityMember member = CommunityMember.builder()
                .id(new CommunityMemberId(creator.getId(), saved.getId()))
                .user(creator)
                .community(saved)
                .joinedAt(Instant.now())
                .build();

        communityMemberRepository.save(member);

        // add creator as moderator
        CommunityModerator moderator = CommunityModerator.builder()
                .id(new CommunityModeratorId(saved.getId(), creator.getId()))
                .community(saved)
                .user(creator)
                .assignedAt(Instant.now())
                .build();

        communityModeratorRepository.save(moderator);

        return toResponse(saved);
    }

    public CommunityResponse getCommunity(String name) {

        Community community = communityRepository.findByNameAndIsDeletedFalse(name)
                .orElseThrow(() -> new BusinessException("Community not found"));

        return toResponse(community);
    }

    public List<CommunityResponse> listCommunities() {

        return communityRepository.findByIsDeletedFalse()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public void joinCommunity(Long communityId, String email) {

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException("User not found"));

        Community community = communityRepository.findById(communityId)
                .orElseThrow(() -> new BusinessException("Community not found"));

        if (communityMemberRepository.existsByUserIdAndCommunityId(user.getId(), communityId)) {
            return;
        }

        CommunityMember member = CommunityMember.builder()
                .id(new CommunityMemberId(user.getId(), communityId))
                .user(user)
                .community(community)
                .joinedAt(Instant.now())
                .build();

        communityMemberRepository.save(member);
    }

    public void addModerator(Long communityId, Long userId, String requesterEmail) {

        Community community = communityRepository.findById(communityId)
                .orElseThrow(() -> new BusinessException("Community not found"));

        User requester = userRepository.findByEmail(requesterEmail)
                .orElseThrow(() -> new BusinessException("User not found"));

        if (!community.getCreatedBy().getId().equals(requester.getId())) {
            throw new BusinessException("Only owner can assign moderators");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("User not found"));

        CommunityModerator moderator = CommunityModerator.builder()
                .id(new CommunityModeratorId(communityId, userId))
                .community(community)
                .user(user)
                .assignedAt(Instant.now())
                .build();

        communityModeratorRepository.save(moderator);
    }

    public void leaveCommunity(Long communityId, String email) {

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException("User not found"));

        Community community = communityRepository.findById(communityId)
                .orElseThrow(() -> new BusinessException("Community not found"));

        if (community.getCreatedBy().getId().equals(user.getId())) {
            throw new BusinessException("Owner cannot leave community");
        }

        CommunityMemberId id = new CommunityMemberId(user.getId(), communityId);

        communityMemberRepository.deleteById(id);
    }

    public void removeModerator(Long communityId, Long userId, String requesterEmail) {

        Community community = communityRepository.findById(communityId)
                .orElseThrow(() -> new BusinessException("Community not found"));

        User requester = userRepository.findByEmail(requesterEmail)
                .orElseThrow(() -> new BusinessException("User not found"));

        if (!community.getCreatedBy().getId().equals(requester.getId())) {
            throw new BusinessException("Only owner can remove moderators");
        }

        CommunityModeratorId id = new CommunityModeratorId(communityId, userId);

        communityModeratorRepository.deleteById(id);
    }

    public void deleteCommunity(Long id, String email) {

        Community community = communityRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Community not found"));

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException("User not found"));

        boolean isOwner = community.getCreatedBy().getId().equals(user.getId());

        boolean isAdmin = user.getRoles()
                .stream()
                .anyMatch(role -> role.getName().equals("ADMIN"));

        if (!isOwner && !isAdmin) {
            throw new BusinessException("Only owner or admin can delete community");
        }

        community.setIsDeleted(true);
        communityRepository.save(community);
        communityMemberRepository.deleteAllByCommunityId(id);
        communityModeratorRepository.deleteAllByCommunityId(id);
    }


    private CommunityResponse toResponse(Community community) {

        long members = communityMemberRepository.countByCommunityId(community.getId());

        return CommunityResponse.builder()
                .id(community.getId())
                .name(community.getName())
                .description(community.getDescription())
                .createdBy(community.getCreatedBy().getUsername())
                .isPrivate(community.getIsPrivate())
                .createdAt(community.getCreatedAt())
                .memberCount(members)
                .build();
    }





    public List<CommunityResponse> getCommunitiesCreatedByUser(String email) {

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException("User not found"));

        return communityRepository.findByCreatedByIdAndIsDeletedFalse(user.getId())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public List<CommunityResponse> getJoinedCommunities(String email) {

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException("User not found"));

        return communityMemberRepository.findByUserId(user.getId())
                .stream()
                .map(member -> toResponse(member.getCommunity()))
                .toList();
    }

    public List<CommunityResponse> getModeratedCommunities(String email) {

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException("User not found"));

        return communityModeratorRepository.findActiveModerations(user.getId())
                .stream()
                .map(mod -> toResponse(mod.getCommunity()))
                .toList();
    }


    //Temporary --> future elastic search
    public List<CommunityResponse> searchCommunities(String query) {

        return communityRepository.findByNameContainingIgnoreCaseAndIsDeletedFalse(query)
                .stream()
                .map(this::toResponse)
                .toList();
    }

}