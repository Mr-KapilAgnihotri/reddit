package com.kapil.reddit.post.service;

import com.kapil.reddit.common.exception.BusinessException;
import com.kapil.reddit.community.domain.Community;
import com.kapil.reddit.community.repository.CommunityMemberRepository;
import com.kapil.reddit.community.repository.CommunityRepository;
import com.kapil.reddit.post.domain.Post;
import com.kapil.reddit.post.domain.PostMedia;
import com.kapil.reddit.post.dto.CreatePostRequest;
import com.kapil.reddit.post.dto.MediaRequest;
import com.kapil.reddit.post.dto.PostResponse;
import com.kapil.reddit.post.dto.UpdatePostRequest;
import com.kapil.reddit.post.mapper.PostMapper;
import com.kapil.reddit.post.repository.PostMediaRepository;
import com.kapil.reddit.post.repository.PostRepository;
import com.kapil.reddit.user.domain.User;
import com.kapil.reddit.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
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

    // ================= CREATE POST =================

    public PostResponse createPost(String email, CreatePostRequest request) {

        User author = userRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException("User not found"));

        Community community = null;

        // Community post
        if (request.getCommunityId() != null) {

            community = communityRepository.findById(request.getCommunityId())
                    .orElseThrow(() -> new BusinessException("Community not found"));

            boolean isMember = communityMemberRepository
                    .existsByUserIdAndCommunityId(author.getId(), community.getId());

            if (!isMember) {
                throw new BusinessException("You must join the community before posting");
            }
        }

        Post post = Post.builder()
                .author(author)
                .community(community)
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

        // Save media
        if (request.getMedia() != null && !request.getMedia().isEmpty()) {

            List<PostMedia> mediaList = request.getMedia().stream()
                    .map(mediaRequest -> PostMedia.builder()
                            .post(saved)
                            .mediaUrl(mediaRequest.getMediaUrl())
                            .mediaType(mediaRequest.getMediaType())
                            .caption(mediaRequest.getCaption())
                            .createdAt(Instant.now())
                            .build())
                    .toList();

            postMediaRepository.saveAll(mediaList);

            // IMPORTANT: attach media to post object
            saved.setMedia(mediaList);
        }

        return PostMapper.toResponse(saved, saved.getMedia());
    }

    // ================= GLOBAL FEED =================

    public Page<PostResponse> getGlobalPosts(int page, int size) {

        Pageable pageable = PageRequest.of(page, size);

        return postRepository
                .findByIsDeletedFalseOrderByCreatedAtDesc(pageable)
                .map(post -> PostMapper.toResponse(post, post.getMedia()));
    }

    // ================= HOME FEED =================

    public Page<PostResponse> getHomeFeed(String email, int page, int size) {

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException("User not found"));

        List<Long> communityIds = communityMemberRepository
                .findByUserId(user.getId())
                .stream()
                .map(member -> member.getCommunity().getId())
                .toList();

        if (communityIds.isEmpty()) {
            return Page.empty();
        }

        Pageable pageable = PageRequest.of(page, size);

        return postRepository
                .findByCommunityIdInAndIsDeletedFalseOrderByCreatedAtDesc(communityIds, pageable)
                .map(post -> PostMapper.toResponse(post, post.getMedia()));
    }

    // ================= USER POSTS =================

    public Page<PostResponse> getUserPosts(String username, int page, int size) {

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException("User not found"));

        Pageable pageable = PageRequest.of(page, size);

        return postRepository
                .findByAuthorIdAndIsDeletedFalseOrderByCreatedAtDesc(user.getId(), pageable)
                .map(post -> PostMapper.toResponse(post, post.getMedia()));
    }

    // ================= COMMUNITY POSTS =================

    public Page<PostResponse> getCommunityPosts(String communityName, int page, int size) {

        Community community = communityRepository.findByNameAndIsDeletedFalse(communityName)
                .orElseThrow(() -> new BusinessException("Community not found"));

        Pageable pageable = PageRequest.of(page, size);

        return postRepository
                .findByCommunityIdAndIsDeletedFalseOrderByCreatedAtDesc(community.getId(), pageable)
                .map(post -> PostMapper.toResponse(post, post.getMedia()));
    }

    // ================= GET SINGLE POST =================

    public PostResponse getPost(Long id) {

        Post post = postRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Post not found"));

        if (Boolean.TRUE.equals(post.getIsDeleted())) {
            throw new BusinessException("Post deleted");
        }

        post.setViewCount(post.getViewCount() + 1);
        postRepository.save(post);

        return PostMapper.toResponse(post, post.getMedia());
    }

    // ================= UPDATE POST =================

    public PostResponse updatePost(Long id, String email, UpdatePostRequest request) {

        Post post = postRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Post not found"));

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException("User not found"));

        if (!post.getAuthor().getId().equals(user.getId())) {
            throw new BusinessException("Only author can edit post");
        }

        post.setTitle(request.getTitle());
        post.setDisplayText(request.getText());
        post.setUpdatedAt(Instant.now());

        postRepository.save(post);

        return PostMapper.toResponse(post, post.getMedia());
    }

    // ================= DELETE POST =================

    public void deletePost(Long id, String email) {

        Post post = postRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Post not found"));

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException("User not found"));

        if (!post.getAuthor().getId().equals(user.getId())) {
            throw new BusinessException("Only author can delete post");
        }

        post.setIsDeleted(true);
        postRepository.save(post);
    }
}