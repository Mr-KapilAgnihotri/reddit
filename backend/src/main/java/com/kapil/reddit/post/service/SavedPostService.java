package com.kapil.reddit.post.service;

import com.kapil.reddit.common.exception.BusinessException;
import com.kapil.reddit.common.exception.ResourceNotFoundException;
import com.kapil.reddit.post.domain.Post;
import com.kapil.reddit.post.domain.SavedPost;
import com.kapil.reddit.post.domain.SavedPostId;
import com.kapil.reddit.post.dto.PostResponse;
import com.kapil.reddit.post.mapper.PostMapper;
import com.kapil.reddit.post.repository.PostRepository;
import com.kapil.reddit.post.repository.SavedPostRepository;
import com.kapil.reddit.post.vote.domain.PostVote;
import com.kapil.reddit.post.vote.repository.PostVoteRepository;
import com.kapil.reddit.user.domain.User;
import com.kapil.reddit.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SavedPostService {

    private final SavedPostRepository savedPostRepository;
    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final PostVoteRepository postVoteRepository;

    // ─── SAVE ────────────────────────────────────────────────────────────────

    @Transactional
    public void savePost(String email, Long postId) {
        User user = getUser(email);

        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post not found"));

        if (Boolean.TRUE.equals(post.getIsDeleted())) {
            throw new BusinessException("Cannot save a deleted post");
        }

        if (savedPostRepository.existsByIdUserIdAndIdPostId(user.getId(), postId)) {
            throw new BusinessException("Post is already saved");
        }

        SavedPost savedPost = SavedPost.builder()
                .id(new SavedPostId(user.getId(), postId))
                .post(post)
                .user(user)
                .savedAt(Instant.now())
                .build();

        savedPostRepository.save(savedPost);
    }

    // ─── UNSAVE ───────────────────────────────────────────────────────────────

    @Transactional
    public void unsavePost(String email, Long postId) {
        User user = getUser(email);

        if (!savedPostRepository.existsByIdUserIdAndIdPostId(user.getId(), postId)) {
            throw new BusinessException("Post is not saved");
        }

        savedPostRepository.deleteByIdUserIdAndIdPostId(user.getId(), postId);
    }

    // ─── GET SAVED FEED ───────────────────────────────────────────────────────

    /**
     * Returns saved posts for the authenticated user as a paginated PostResponse list.
     *
     * N+1 avoided: findByUserIdWithPost uses a JOIN FETCH that eagerly loads
     * Post, Post.community, and Post.author in a single SQL query.
     *
     * Sort: the repository always returns newest-saved-first; page/size applied
     * in memory (saved feeds are typically small and not cached independently).
     */
    @Transactional(readOnly = true)
    public Page<PostResponse> getSavedPosts(String email, int page, int size) {
        User user = getUser(email);

        List<SavedPost> all = savedPostRepository.findByUserIdWithPost(user.getId());

        // Apply pagination in-memory (saves are bounded by user behavior)
        int start = page * size;
        int end   = Math.min(start + size, all.size());

        List<PostResponse> pageContent = all.subList(Math.min(start, all.size()), end)
                .stream()
                .map(sp -> {
                    Post post = sp.getPost();
                    Short userVote = postVoteRepository
                            .findByPostIdAndUserId(post.getId(), user.getId())
                            .map(PostVote::getValue)
                            .orElse((short) 0);
                    return PostMapper.toResponse(post, post.getMedia(), userVote);
                })
                .toList();

        return new PageImpl<>(pageContent, PageRequest.of(page, size), all.size());
    }

    // ─── HELPERS ──────────────────────────────────────────────────────────────

    private User getUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException("User not found"));
    }
}
