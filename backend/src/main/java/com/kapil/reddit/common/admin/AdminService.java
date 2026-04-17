package com.kapil.reddit.common.admin;

import com.kapil.reddit.comment.domain.Comment;
import com.kapil.reddit.comment.repository.CommentRepository;
import com.kapil.reddit.common.exception.BusinessException;
import com.kapil.reddit.common.exception.ResourceNotFoundException;
import com.kapil.reddit.post.domain.Post;
import com.kapil.reddit.post.dto.AdminModerationRequest;
import com.kapil.reddit.post.repository.PostRepository;
import com.kapil.reddit.user.domain.User;
import com.kapil.reddit.user.dto.BanUserRequest;
import com.kapil.reddit.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminService {

    private final PostRepository    postRepository;
    private final CommentRepository commentRepository;
    private final UserRepository    userRepository;

    // ─── FORCE-DELETE POST ────────────────────────────────────────────────────

    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "postDetail",  allEntries = true),
            @CacheEvict(value = "globalFeed",  allEntries = true),
            @CacheEvict(value = "homeFeed",    allEntries = true),
            @CacheEvict(value = "comments",    allEntries = true)
    })
    public void adminDeletePost(Long postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post not found: " + postId));
        post.setIsDeleted(true);
        post.setUpdatedAt(Instant.now());
        postRepository.save(post);
        log.info("ADMIN: force-deleted post {}", postId);
    }

    // ─── FORCE-DELETE COMMENT ─────────────────────────────────────────────────

    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "comments",   allEntries = true),
            @CacheEvict(value = "postDetail", allEntries = true)
    })
    public void adminDeleteComment(Long commentId) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new ResourceNotFoundException("Comment not found: " + commentId));
        comment.setIsDeleted(true);
        commentRepository.save(comment);
        log.info("ADMIN: force-deleted comment {}", commentId);
    }

    // ─── OVERRIDE POST MODERATION STATUS ─────────────────────────────────────

    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "postDetail", allEntries = true),
            @CacheEvict(value = "globalFeed", allEntries = true),
            @CacheEvict(value = "homeFeed",   allEntries = true)
    })
    public void overridePostModeration(Long postId, AdminModerationRequest request) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post not found: " + postId));
        if (request.getIsModerated() != null) {
            post.setIsModerated(request.getIsModerated());
        }
        if (request.getDisplayText() != null) {
            post.setDisplayText(request.getDisplayText());
        }
        post.setUpdatedAt(Instant.now());
        postRepository.save(post);
        log.info("ADMIN: overrode moderation on post {} → isModerated={}", postId, request.getIsModerated());
    }

    // ─── OVERRIDE COMMENT MODERATION STATUS ──────────────────────────────────

    @Transactional
    @CacheEvict(value = "comments", allEntries = true)
    public void overrideCommentModeration(Long commentId, AdminModerationRequest request) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new ResourceNotFoundException("Comment not found: " + commentId));
        if (request.getIsModerated() != null) {
            comment.setIsModerated(request.getIsModerated());
        }
        if (request.getDisplayText() != null) {
            comment.setDisplayText(request.getDisplayText());
        }
        commentRepository.save(comment);
        log.info("ADMIN: overrode moderation on comment {}", commentId);
    }

    // ─── BAN USER ─────────────────────────────────────────────────────────────

    /**
     * Bans a user by setting isActive=false.
     *
     * JWT handling strategy: We use SHORT-LIVED access tokens (15 min TTL by default).
     * When a user is banned, their existing tokens remain technically valid until expiry,
     * but {@link com.kapil.reddit.auth.security.CustomUserDetailsService} reloads the
     * user from DB on every request and checks isActive — so the ban takes effect
     * within one token TTL cycle at most.
     *
     * For immediate revocation in high-security scenarios, a Redis token blocklist
     * (storing jti claim → expiry) would eliminate the TTL window entirely.
     */
    @Transactional
    public void banUser(Long userId, BanUserRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
        if (Boolean.FALSE.equals(user.getIsActive())) {
            throw new BusinessException("User is already banned");
        }
        user.setIsActive(false);
        user.setBanReason(request != null ? request.getReason() : null);
        userRepository.save(user);
        log.info("ADMIN: banned user {} (reason={})", userId, request != null ? request.getReason() : "none");
    }

    // ─── UNBAN USER ───────────────────────────────────────────────────────────

    @Transactional
    public void unbanUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
        if (Boolean.TRUE.equals(user.getIsActive())) {
            throw new BusinessException("User is not banned");
        }
        user.setIsActive(true);
        user.setBanReason(null);
        userRepository.save(user);
        log.info("ADMIN: unbanned user {}", userId);
    }
}
