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
import com.kapil.reddit.post.event.ModerationResultEvent;
import com.kapil.reddit.post.event.PostProducer;
import com.kapil.reddit.post.mapper.PostMapper;
import com.kapil.reddit.post.repository.PostMediaRepository;
import com.kapil.reddit.post.repository.PostRepository;
import com.kapil.reddit.post.vote.domain.PostVote;
import com.kapil.reddit.post.vote.repository.PostVoteRepository;
import com.kapil.reddit.post.repository.SavedPostRepository;
import com.kapil.reddit.user.domain.User;
import com.kapil.reddit.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class PostService {

    private final PostRepository postRepository;
    private final PostMediaRepository postMediaRepository;
    private final CommunityRepository communityRepository;
    private final UserRepository userRepository;
    private final CommunityMemberRepository communityMemberRepository;
    private final PostVoteRepository postVoteRepository;
    private final PostProducer postProducer;
    private final SavedPostRepository savedPostRepository;

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
        postProducer.sendPostCreatedEvent(saved);

        return PostMapper.toResponse(saved, saved.getMedia(), (short) 0);
    }

    // ================= GLOBAL FEED =================

    /**
     * sync=true activates stampede protection: only one thread populates the cache
     * on expiry; concurrent requests wait rather than all hitting the DB.
     */
    @Transactional(readOnly = true)
    @Cacheable(
            value = "globalFeed",
            key   = "(#email != null ? #email.toLowerCase().trim() : 'anon') + '-' + #page + '-' + #size + '-' + #sort"
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
    @Transactional(readOnly = true)
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

    @Transactional(readOnly = true)
    public Page<PostResponse> getUserPosts(String requestingEmail, String username, int page, int size, String sort) {
        Long requestingUserId = getUserIdOrNull(requestingEmail);

        User targetUser = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException("User not found"));

        Pageable pageable = PageRequest.of(page, size, resolveSort(sort));
        Page<Post> posts = postRepository.findByAuthorIdAndIsDeletedFalse(targetUser.getId(), pageable);
        return mapPosts(posts, requestingUserId);
    }

    // ================= COMMUNITY POSTS =================

    @Transactional(readOnly = true)
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

    @Transactional
    @Cacheable(value = "postDetail", key = "#id + '-' + (#email != null ? #email.toLowerCase().trim() : 'anon')")
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

    // ================= MODERATION RESULT (from Kafka) =================

    /**
     * Called by {@link com.kapil.reddit.post.event.ModerationConsumer} when the
     * ML service has finished processing a post.
     *
     * <p>Updates:
     * <ul>
     *   <li>{@code displayText} → censored text from the ML service</li>
     *   <li>{@code isModerated} → whether profanity was detected</li>
     * </ul>
     *
     * <p>Idempotent: safe to call multiple times for the same post.
     * <p>If the post no longer exists the event is logged and silently ignored.
     */
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "postDetail", allEntries = true),
            @CacheEvict(value = "globalFeed", allEntries = true),
            @CacheEvict(value = "homeFeed",   allEntries = true)
    })
    public void handleModerationResult(ModerationResultEvent event) {
        if (event == null || event.getPostId() == null) {
            log.warn("handleModerationResult called with null event or null postId — ignoring");
            return;
        }

        postRepository.findById(event.getPostId()).ifPresentOrElse(
                post -> {
                    String maskedText = event.getMaskedText();
                    if (maskedText != null) {
                        post.setDisplayText(maskedText);
                    }
                    post.setIsModerated(event.isModerated());
                    post.setUpdatedAt(Instant.now());
                    postRepository.save(post);
                    log.info("Moderation result applied | postId={} isModerated={}",
                            event.getPostId(), event.isModerated());
                },
                () -> log.warn("handleModerationResult: post not found for postId={} — ignoring",
                        event.getPostId())
        );
    }

    // ================= STORE EMBEDDING (from Kafka post-embedding topic) =================

    /**
     * Persists the 384-dimensional text embedding produced by the ML service.
     * Called by {@link com.kapil.reddit.post.event.EmbeddingConsumer}.
     * Idempotent — safe to re-call if the Kafka message is re-delivered.
     */
    @Transactional
    public void storeEmbedding(com.kapil.reddit.post.event.EmbeddingEvent event) {
        if (event == null || event.getPostId() == null || event.getEmbedding() == null) {
            log.warn("storeEmbedding: invalid event — skipping");
            return;
        }
        postRepository.findById(event.getPostId()).ifPresentOrElse(
                post -> {
                    // Convert List<Float> → float[]
                    java.util.List<Float> list = event.getEmbedding();
                    float[] arr = new float[list.size()];
                    for (int i = 0; i < list.size(); i++) arr[i] = list.get(i);
                    post.setEmbedding(arr);
                    postRepository.save(post);
                    log.info("Embedding stored | postId={} dim={}", event.getPostId(), arr.length);
                },
                () -> log.warn("storeEmbedding: post not found for postId={} — ignoring", event.getPostId())
        );
    }

    // ================= RECOMMENDED FEED (PGVector cosine similarity) =================

    /**
     * Returns a personalized recommendation feed using PGVector cosine similarity.
     *
     * <p>Algorithm:
     * <ol>
     *   <li>Collect the user's last 5 upvoted AND all saved post IDs as "positive signals".</li>
     *   <li>Fetch those posts' embeddings from the DB.</li>
     *   <li>Average the embeddings into a single query vector.</li>
     *   <li>Run a PGVector cosine similarity search to find the top-N most similar posts.</li>
     *   <li>Exclude posts the user has already authored, upvoted, or saved (anti-join).</li>
     * </ol>
     *
     * <p>If the user has no upvoted/saved posts with embeddings, falls back to the
     * global feed (newest first).
     */
    @Transactional(readOnly = true)
    public Page<PostResponse> getRecommendedFeed(String email, int page, int size) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException("User not found"));

        Long userId = user.getId();

        // 1. Collect positive-signal post IDs (upvoted + saved)
        java.util.List<Long> upvotedIds = postRepository.findUpvotedPostIdsByUserId(userId);
        java.util.List<Long> savedIds   = savedPostRepository.findPostIdsByUserId(userId);

        // Reference signals = up to last 5 upvoted + last 5 saved (deduplicated)
        java.util.Set<Long> signalIds = new java.util.LinkedHashSet<>();
        upvotedIds.stream().limit(5).forEach(signalIds::add);
        savedIds.stream().limit(5).forEach(signalIds::add);

        // 2. Build exclusion list: authored + upvoted + saved
        java.util.Set<Long> excludedIds = new java.util.HashSet<>();
        postRepository.findByAuthorIdAndIsDeletedFalse(userId, PageRequest.of(0, 200))
                .getContent().stream().map(Post::getId).forEach(excludedIds::add);
        excludedIds.addAll(upvotedIds);
        excludedIds.addAll(savedIds);

        // 3. Fetch reference embeddings
        java.util.List<Post> referencePosts = signalIds.isEmpty()
                ? java.util.List.of()
                : postRepository.findByIdInWithEmbedding(java.util.List.copyOf(signalIds));

        // 4. Average reference embeddings → query vector
        float[] queryVector = averageEmbeddings(referencePosts);

        if (queryVector == null) {
            // No reference embeddings — fall back to global feed (newest first)
            log.info("RecommendedFeed: no reference embeddings for user {} → fallback to global", email);
            org.springframework.data.domain.Pageable pageable = PageRequest.of(page, size,
                    Sort.by(Sort.Direction.DESC, "createdAt"));
            return mapPosts(postRepository.findByIsDeletedFalse(pageable), userId);
        }

        // 5. Serialise as pgvector literal: "[f1,f2,...,f384]"
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < queryVector.length; i++) {
            sb.append(queryVector[i]);
            if (i < queryVector.length - 1) sb.append(',');
        }
        sb.append(']');
        String pgVectorLiteral = sb.toString();

        java.util.List<Long> excludedList = excludedIds.isEmpty()
                ? java.util.List.of(-1L)   // dummy so IN clause is valid SQL
                : java.util.List.copyOf(excludedIds);

        // 6. Cosine similarity search via PGVector (native query in PostRepository)
        int fetchLimit = (page + 1) * size + size;  // over-fetch to support pagination
        java.util.List<Post> similar = postRepository.findSimilarPosts(
                pgVectorLiteral,
                excludedList,
                excludedIds.size(),
                fetchLimit
        );

        // 7. Manual pagination over the result list
        int start = page * size;
        int end   = Math.min(start + size, similar.size());
        java.util.List<Post> pageContent = start >= similar.size()
                ? java.util.List.of()
                : similar.subList(start, end);

        java.util.List<PostResponse> responses = pageContent.stream()
                .map(p -> PostMapper.toResponse(p, p.getMedia(), getUserVote(p.getId(), userId)))
                .toList();

        return new org.springframework.data.domain.PageImpl<>(
                responses,
                PageRequest.of(page, size),
                similar.size()
        );
    }

    /** Averages the embeddings of the given posts. Returns null if none have embeddings. */
    private float[] averageEmbeddings(java.util.List<Post> posts) {
        java.util.List<float[]> embeddings = posts.stream()
                .map(Post::getEmbedding)
                .filter(e -> e != null && e.length == 384)
                .toList();
        if (embeddings.isEmpty()) return null;
        float[] avg = new float[384];
        for (float[] e : embeddings) {
            for (int i = 0; i < 384; i++) avg[i] += e[i];
        }
        for (int i = 0; i < 384; i++) avg[i] /= embeddings.size();
        return avg;
    }
}