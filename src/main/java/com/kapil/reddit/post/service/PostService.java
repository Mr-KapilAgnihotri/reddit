package com.kapil.reddit.post.service;

import com.kapil.reddit.common.exception.BusinessException;
import com.kapil.reddit.common.exception.ResourceNotFoundException;
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
import com.kapil.reddit.post.vote.domain.PostVote;
import com.kapil.reddit.post.vote.repository.PostVoteRepository;
import com.kapil.reddit.user.domain.User;
import com.kapil.reddit.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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
    private final PostVoteRepository postVoteRepository;

    private Sort resolveSort(String sort) {
        if ("top".equalsIgnoreCase(sort)) {
            return Sort.by(Sort.Direction.DESC, "score");
        } else if ("hot".equalsIgnoreCase(sort)) {
            return Sort.by(Sort.Direction.DESC, "hotScore");
        }
        return Sort.by(Sort.Direction.DESC, "createdAt"); // Default is new
    }

    private Long getUserIdOrNull(String email) {
        if (email == null)
            return null;
        return userRepository.findByEmail(email).map(User::getId).orElse(null);
    }

    private Short getUserVote(Long postId, Long userId) {
        if (userId == null)
            return 0;
        return postVoteRepository.findByPostIdAndUserId(postId, userId)
                .map(PostVote::getValue)
                .orElse((short) 0);
    }

    private Page<PostResponse> mapPosts(Page<Post> posts, Long userId) {
        return posts.map(post -> {
            Short userVote = getUserVote(post.getId(), userId);
            return PostMapper.toResponse(post, post.getMedia(), userVote);
        });
    }

    // ================= CREATE POST =================

    @Caching(evict = {
            @CacheEvict(value = "globalFeed", allEntries = true),
            @CacheEvict(value = "homeFeed",   allEntries = true)
    })
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
                .isModerated(false)
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

        return PostMapper.toResponse(saved, saved.getMedia(), (short) 0);
    }

    // ================= GLOBAL FEED =================

    /**
     * sync=true activates stampede protection: only one thread populates the cache
     * on expiry; concurrent requests wait rather than all hitting the DB.
     */
    @Cacheable(
            value = "globalFeed",
            key   = "#page + '-' + #size + '-' + #sort"
    )
    public Page<PostResponse> getGlobalPosts(String email, int page, int size, String sort) {
        Long userId = getUserIdOrNull(email);
        Pageable pageable = PageRequest.of(page, size, resolveSort(sort));
        Page<Post> posts = postRepository.findByIsDeletedFalse(pageable);
        return mapPosts(posts, userId);
    }

    // ================= HOME FEED =================

    /**
     * Cache key includes normalized email (lowercase, trimmed) so each user
     * gets their own cache bucket — prevents cross-user userVote leakage.
     * Email is always non-null here (authenticated endpoint).
     */
    @Cacheable(
            value = "homeFeed",
            key   = "#email.toLowerCase().trim() + '-' + #page + '-' + #size + '-' + #sort"
    )
    public Page<PostResponse> getHomeFeed(String email, int page, int size, String sort) {
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

        Pageable pageable = PageRequest.of(page, size, resolveSort(sort));
        Page<Post> posts = postRepository.findByCommunityIdInAndIsDeletedFalse(communityIds, pageable);
        return mapPosts(posts, user.getId());
    }

    // ================= USER POSTS =================

    public Page<PostResponse> getUserPosts(String requestingEmail, String username, int page, int size, String sort) {
        Long requestingUserId = getUserIdOrNull(requestingEmail);

        User targetUser = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException("User not found"));

        Pageable pageable = PageRequest.of(page, size, resolveSort(sort));
        Page<Post> posts = postRepository.findByAuthorIdAndIsDeletedFalse(targetUser.getId(), pageable);
        return mapPosts(posts, requestingUserId);
    }

    // ================= COMMUNITY POSTS =================

    public Page<PostResponse> getCommunityPosts(String requestingEmail, String communityName, int page, int size,
            String sort) {
        Long requestingUserId = getUserIdOrNull(requestingEmail);

        Community community = communityRepository.findByNameAndIsDeletedFalse(communityName)
                .orElseThrow(() -> new BusinessException("Community not found"));

        Pageable pageable = PageRequest.of(page, size, resolveSort(sort));
        Page<Post> posts = postRepository.findByCommunityIdAndIsDeletedFalse(community.getId(), pageable);
        return mapPosts(posts, requestingUserId);
    }

    // ================= GET SINGLE POST =================

    @Cacheable(value = "postDetail", key = "#id")
    public PostResponse getPost(Long id, String email) {
        Post post = postRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Post not found"));

        if (Boolean.TRUE.equals(post.getIsDeleted())) {
            throw new ResourceNotFoundException("Post not found");
        }

        post.setViewCount(post.getViewCount() + 1);
        postRepository.save(post);

        Long userId = getUserIdOrNull(email);
        Short userVote = getUserVote(id, userId);

        return PostMapper.toResponse(post, post.getMedia(), userVote);
    }

    // ================= UPDATE POST =================

    @Caching(evict = {
            @CacheEvict(value = "postDetail", allEntries = true),
            @CacheEvict(value = "globalFeed", allEntries = true),
            @CacheEvict(value = "homeFeed",   allEntries = true)
    })
    public PostResponse updatePost(Long id, String email, UpdatePostRequest request) {

        Post post = postRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Post not found"));

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException("User not found"));

        if (!post.getAuthor().getId().equals(user.getId())) {
            throw new BusinessException("Only author can edit post");
        }

        // Only update fields that were actually provided (null = not supplied in request)
        if (request.getTitle() != null) {
            post.setTitle(request.getTitle());
        }
        if (request.getText() != null) {
            post.setDisplayText(request.getText());
        }
        post.setUpdatedAt(Instant.now());

        postRepository.save(post);
        Short userVote = getUserVote(id, user.getId());

        return PostMapper.toResponse(post, post.getMedia(), userVote);
    }

    // ================= DELETE POST =================

    @Caching(evict = {
            @CacheEvict(value = "postDetail", allEntries = true),
            @CacheEvict(value = "globalFeed", allEntries = true),
            @CacheEvict(value = "homeFeed",   allEntries = true)
    })
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