package com.kapil.reddit.comment.service;

import com.kapil.reddit.comment.domain.Comment;
import com.kapil.reddit.comment.domain.CommentVote;
import com.kapil.reddit.comment.dto.CommentResponse;
import com.kapil.reddit.comment.dto.CreateCommentRequest;
import com.kapil.reddit.comment.dto.UpdateCommentRequest;
import com.kapil.reddit.comment.mapper.CommentMapper;
import com.kapil.reddit.comment.repository.CommentRepository;
import com.kapil.reddit.comment.repository.CommentVoteRepository;
import com.kapil.reddit.common.exception.BusinessException;
import com.kapil.reddit.common.exception.ResourceNotFoundException;
import com.kapil.reddit.post.domain.Post;
import com.kapil.reddit.post.repository.PostRepository;
import com.kapil.reddit.user.domain.User;
import com.kapil.reddit.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CommentService {

    private final CommentRepository commentRepository;
    private final CommentVoteRepository commentVoteRepository;
    private final PostRepository postRepository;
    private final UserRepository userRepository;

    // ========================= CREATE COMMENT =========================

    @Transactional
    public CommentResponse createComment(String email, CreateCommentRequest request) {

        User author = userRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException("User not found"));

        Post post = postRepository.findById(request.getPostId())
                .orElseThrow(() -> new BusinessException("Post not found"));

        Comment parentComment = null;
        if (request.getParentCommentId() != null) {
            parentComment = commentRepository.findById(request.getParentCommentId())
                    .orElseThrow(() -> new ResourceNotFoundException("Parent comment not found"));

            // Cross-post nesting guard — parent must belong to the same post
            if (!parentComment.getPost().getId().equals(post.getId())) {
                throw new BusinessException("Parent comment does not belong to this post");
            }
        }

        // originalText = raw user input (immutable — reserved for ML pipeline)
        // displayText  = currently identical; ML will update this field later
        Comment comment = Comment.builder()
                .post(post)
                .parentComment(parentComment)
                .author(author)
                .originalText(request.getText())
                .displayText(request.getText())
                .upvotes(0L)
                .downvotes(0L)
                .score(0L)
                .isDeleted(false)
                .isModerated(false)
                .createdAt(Instant.now())
                .build();

        Comment saved = commentRepository.save(comment);

        // Keep post.commentCount in sync
        post.setCommentCount(post.getCommentCount() + 1);
        postRepository.save(post);

        return CommentMapper.toResponse(saved, (short) 0, List.of());
    }

    // ========================= GET COMMENT TREE =========================

    /**
     * Builds a paginated, nested comment tree for a post with NO N+1 queries.
     *
     * Strategy:
     *   1. Paginate root comments (parent IS NULL) — 1 query.
     *   2. Fetch ALL comments for the post in one flat query — 1 query.
     *   3. Batch-fetch all votes for the current user — 1 query.
     *   4. Group children by parentId and build the tree in memory.
     *
     * Total DB hits: 3 regardless of nesting depth or comment count.
     */
    @Transactional(readOnly = true)
    public Page<CommentResponse> getCommentsForPost(Long postId, String email, int page, int size) {

        if (!postRepository.existsById(postId)) {
            throw new BusinessException("Post not found");
        }

        Pageable pageable = PageRequest.of(page, size);

        // 1. Paginated roots (author JOIN FETCHed inside query)
        Page<Comment> rootPage = commentRepository.findRootsByPostId(postId, pageable);

        if (rootPage.isEmpty()) {
            return Page.empty(pageable);
        }

        // 2. All comments for this post (single flat query, author JOIN FETCHed)
        List<Comment> allComments = commentRepository.findAllByPostId(postId);

        // Collect all comment IDs for the batch vote lookup
        List<Long> allCommentIds = allComments.stream()
                .map(Comment::getId)
                .collect(Collectors.toList());

        // 3. Batch-fetch votes for the current user (0 iterations for unauthenticated users)
        Map<Long, Short> voteMap = buildVoteMap(email, allCommentIds);

        // 4. Group children by parentId — O(n), no DB calls
        Map<Long, List<Comment>> childrenMap = allComments.stream()
                .filter(c -> c.getParentComment() != null)
                .collect(Collectors.groupingBy(c -> c.getParentComment().getId()));

        // Convert each root on the requested page to a recursive CommentResponse
        List<CommentResponse> rootResponses = rootPage.getContent().stream()
                .map(root -> buildTree(root, childrenMap, voteMap, new HashSet<>()))
                .collect(Collectors.toList());

        return new PageImpl<>(rootResponses, pageable, rootPage.getTotalElements());
    }

    /**
     * Recursively builds a CommentResponse tree from in-memory maps.
     * Uses a visited set to guard against data-corruption cycles (defensive).
     *
     * @param comment     current node
     * @param childrenMap parentId → list of child Comments (pre-grouped)
     * @param voteMap     commentId → user's vote value
     * @param visited     set of already-processed IDs (cycle guard)
     */
    private CommentResponse buildTree(
            Comment comment,
            Map<Long, List<Comment>> childrenMap,
            Map<Long, Short> voteMap,
            Set<Long> visited) {

        if (visited.contains(comment.getId())) {
            // Defensive: should never happen with clean data, but prevents infinite recursion
            return CommentMapper.toResponse(comment, voteMap.getOrDefault(comment.getId(), (short) 0), List.of());
        }
        visited.add(comment.getId());

        List<Comment> children = childrenMap.getOrDefault(comment.getId(), List.of());

        // Children are already sorted by createdAt ASC (ORDER BY in the repository query)
        List<CommentResponse> childResponses = children.stream()
                .map(child -> buildTree(child, childrenMap, voteMap, visited))
                .collect(Collectors.toList());

        Short userVote = voteMap.getOrDefault(comment.getId(), (short) 0);

        return CommentMapper.toResponse(comment, userVote, childResponses);
    }

    /**
     * Batch-fetches the current user's votes for all given commentIds.
     * Returns an empty map when the user is unauthenticated (email == null).
     */
    private Map<Long, Short> buildVoteMap(String email, List<Long> commentIds) {
        if (email == null || commentIds.isEmpty()) {
            return new HashMap<>();
        }
        return userRepository.findByEmail(email)
                .map(user -> {
                    List<CommentVote> votes = commentVoteRepository
                            .findByUserIdAndCommentIdIn(user.getId(), commentIds);
                    return votes.stream()
                            .collect(Collectors.toMap(CommentVote::getCommentId, CommentVote::getValue));
                })
                .orElse(new HashMap<>());
    }

    // ========================= UPDATE COMMENT =========================

    /**
     * Updates the displayText of a comment.
     * Rules:
     *  - Only the original author can edit.
     *  - Soft-deleted comments cannot be edited.
     *  - originalText is never changed (audit trail for ML).
     */
    @Transactional
    public CommentResponse updateComment(Long commentId, String email, UpdateCommentRequest request) {

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException("User not found"));

        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new ResourceNotFoundException("Comment not found"));

        if (Boolean.TRUE.equals(comment.getIsDeleted())) {
            throw new BusinessException("Cannot edit a deleted comment");
        }

        if (!comment.getAuthor().getId().equals(user.getId())) {
            throw new BusinessException("Only the author can edit this comment");
        }

        // displayText is updated; originalText stays intact for ML pipeline audit
        comment.setDisplayText(request.getText());
        commentRepository.save(comment);

        return CommentMapper.toResponse(comment, (short) 0, List.of());
    }

    // ========================= VOTE COMMENT =========================

    /**
     * Voting logic mirrors PostVoteService exactly:
     *   - New vote   → save
     *   - Same vote  → remove (toggle off)
     *   - Diff vote  → switch
     */
    @Transactional
    public CommentResponse voteComment(Long commentId, String email, short newValue) {

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException("User not found"));

        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new BusinessException("Comment not found"));

        if (Boolean.TRUE.equals(comment.getIsDeleted())) {
            throw new BusinessException("Cannot vote on a deleted comment");
        }

        CommentVote existingVote = commentVoteRepository
                .findByCommentIdAndUserId(commentId, user.getId())
                .orElse(null);

        Short finalUserVote = newValue;

        if (existingVote == null) {
            // NEW VOTE
            commentVoteRepository.save(CommentVote.builder()
                    .commentId(commentId)
                    .userId(user.getId())
                    .value(newValue)
                    .createdAt(Instant.now())
                    .build());
            applyVote(comment, newValue, 1);

        } else if (existingVote.getValue() == newValue) {
            // TOGGLE OFF (same vote again)
            commentVoteRepository.delete(existingVote);
            applyVote(comment, newValue, -1);
            finalUserVote = 0;

        } else {
            // SWITCH VOTE
            short oldValue = existingVote.getValue();
            existingVote.setValue(newValue);
            commentVoteRepository.save(existingVote);
            applyVote(comment, oldValue, -1);
            applyVote(comment, newValue, 1);
        }

        updateScore(comment);
        commentRepository.save(comment);

        return CommentMapper.toResponse(comment, finalUserVote, List.of());
    }

    private void applyVote(Comment comment, short value, int delta) {
        if (value == 1) {
            comment.setUpvotes(comment.getUpvotes() + delta);
        } else {
            comment.setDownvotes(comment.getDownvotes() + delta);
        }
    }

    private void updateScore(Comment comment) {
        comment.setScore(comment.getUpvotes() - comment.getDownvotes());
    }

    // ========================= DELETE COMMENT =========================

    /**
     * Soft deletes a comment (Reddit behavior):
     *  - Sets isDeleted = true; displayText/originalText remain for moderation audit.
     *  - Children are preserved and shown as normal.
     *  - Parent comment may already be deleted — replies are still allowed (Reddit behavior).
     *  - Decrements post.commentCount (floor: 0).
     */
    @Transactional
    public void deleteComment(Long commentId, String email) {

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException("User not found"));

        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new BusinessException("Comment not found"));

        if (!comment.getAuthor().getId().equals(user.getId())) {
            throw new BusinessException("Only the author can delete this comment");
        }

        if (Boolean.TRUE.equals(comment.getIsDeleted())) {
            throw new BusinessException("Comment is already deleted");
        }

        comment.setIsDeleted(true);
        commentRepository.save(comment);

        // Decrement post.commentCount, never below 0
        Post post = comment.getPost();
        long currentCount = post.getCommentCount() != null ? post.getCommentCount() : 0L;
        post.setCommentCount(Math.max(0L, currentCount - 1));
        postRepository.save(post);
    }
}
