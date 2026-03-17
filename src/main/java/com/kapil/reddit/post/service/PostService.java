package com.kapil.reddit.post.service;

import com.kapil.reddit.common.exception.BusinessException;
import com.kapil.reddit.community.domain.Community;
import com.kapil.reddit.community.repository.CommunityMemberRepository;
import com.kapil.reddit.community.repository.CommunityRepository;
import com.kapil.reddit.post.domain.Post;
import com.kapil.reddit.post.domain.PostMedia;
import com.kapil.reddit.post.dto.CreatePostRequest;
import com.kapil.reddit.post.dto.MediaRequest;
import com.kapil.reddit.post.dto.PostMediaResponse;
import com.kapil.reddit.post.dto.PostResponse;
import com.kapil.reddit.post.repository.PostMediaRepository;
import com.kapil.reddit.post.repository.PostRepository;
import com.kapil.reddit.user.domain.User;
import com.kapil.reddit.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PostService {

    private final PostRepository postRepository;
    private final PostMediaRepository postMediaRepository;
    private final CommunityRepository communityRepository;
    private final UserRepository userRepository;
    private final CommunityMemberRepository communityMemberRepository;

    public PostResponse createPost(String email, CreatePostRequest request) {

        User author = userRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException("User not found"));

        Community community = null;

        // Scenario 1: Posting inside a community
        if (request.getCommunityId() != null) {

            community = communityRepository.findById(request.getCommunityId())
                    .orElseThrow(() -> new BusinessException("Community not found"));

            boolean isMember = communityMemberRepository
                    .existsByUserIdAndCommunityId(author.getId(), community.getId());

            if (!isMember) {
                throw new BusinessException("You must join the community before posting");
            }
        }

        // Scenario 2: Profile post (no community)

        Post post = Post.builder()
                .author(author)
                .community(community) // null allowed for profile posts
                .title(request.getTitle())
                .originalText(request.getText())
                .displayText(request.getText())
                .upvotes(0L)
                .downvotes(0L)
                .score(0L)
                .commentCount(0L)
                .viewCount(0L)
                .hotScore(0.0)
                .isDeleted(false)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        Post saved = postRepository.save(post);

        // Handle media
        if (request.getMedia() != null && !request.getMedia().isEmpty()) {

            for (MediaRequest mediaRequest : request.getMedia()) {

                PostMedia media = PostMedia.builder()
                        .post(saved)
                        .mediaUrl(mediaRequest.getMediaUrl())
                        .mediaType(mediaRequest.getMediaType())
                        .caption(mediaRequest.getCaption())
                        .createdAt(Instant.now())
                        .build();

                postMediaRepository.save(media);
            }
        }

        return toResponse(saved);
    }



    public List<PostResponse> getUserPosts(String username) {

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException("User not found"));

        return postRepository.findByAuthorIdAndIsDeletedFalseOrderByCreatedAtDesc(user.getId())
                .stream()
                .map(this::toResponse)
                .toList();
    }




    public List<PostResponse> getCommunityPosts(String communityName) {

        Community community = communityRepository.findByNameAndIsDeletedFalse(communityName)
                .orElseThrow(() -> new BusinessException("Community not found"));

        return postRepository.findByCommunityIdAndIsDeletedFalseOrderByCreatedAtDesc(community.getId())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public void deletePost(Long id, String email) {

        Post post = postRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Post not found"));

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException("User not found"));

        boolean isAuthor = post.getAuthor().getId().equals(user.getId());

        if (!isAuthor) {
            throw new BusinessException("Only author can delete post");
        }

        post.setIsDeleted(true);

        postRepository.save(post);
    }

    private PostResponse toResponse(Post post) {

        List<PostMediaResponse> media = postMediaRepository.findByPostId(post.getId())
                .stream()
                .map(m -> PostMediaResponse.builder()
                        .mediaUrl(m.getMediaUrl())
                        .mediaType(m.getMediaType())
                        .caption(m.getCaption())
                        .build())
                .toList();

        return PostResponse.builder()
                .id(post.getId())
                .title(post.getTitle())
                .text(post.getDisplayText())
                .author(post.getAuthor().getUsername())
                .community(
                        post.getCommunity() != null
                                ? post.getCommunity().getName()
                                : null
                )
                .score(post.getScore())
                .upvotes(post.getUpvotes())
                .downvotes(post.getDownvotes())
                .commentCount(post.getCommentCount())
                .createdAt(post.getCreatedAt())
                .media(media)
                .build();
    }
    public List<PostResponse> getGlobalPosts() {

        return postRepository
                .findByIsDeletedFalseOrderByCreatedAtDesc()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public List<PostResponse> getHomeFeed(String email) {

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException("User not found"));

        List<Long> communityIds = communityMemberRepository
                .findByUserId(user.getId())
                .stream()
                .map(member -> member.getCommunity().getId())
                .toList();

        return postRepository
                .findByCommunityIdInAndIsDeletedFalseOrderByCreatedAtDesc(communityIds)
                .stream()
                .map(this::toResponse)
                .toList();
    }


    public PostResponse getPost(Long id) {

        Post post = postRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Post not found"));

        if (Boolean.TRUE.equals(post.getIsDeleted())) {
            throw new BusinessException("Post deleted");
        }

        return toResponse(post);
    }
}
