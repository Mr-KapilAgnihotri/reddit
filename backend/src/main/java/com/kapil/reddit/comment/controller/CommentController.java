package com.kapil.reddit.comment.controller;

import com.kapil.reddit.comment.dto.CommentResponse;
import com.kapil.reddit.comment.dto.CommentVoteRequest;
import com.kapil.reddit.comment.dto.CreateCommentRequest;
import com.kapil.reddit.comment.dto.UpdateCommentRequest;
import com.kapil.reddit.comment.service.CommentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class CommentController {

    private final CommentService commentService;

    // ─── POST /api/comments ────────────────────────────────────────────────────
    /** Create a root comment or a reply. Requires authentication. */
    @PostMapping("/api/comments")
    @ResponseStatus(HttpStatus.CREATED)
    public CommentResponse createComment(
            @Valid @RequestBody CreateCommentRequest request,
            Authentication authentication
    ) {
        return commentService.createComment(authentication.getName(), request);
    }

    // ─── GET /api/posts/{postId}/comments ─────────────────────────────────────
    /**
     * Returns a paginated, nested comment tree for a post.
     * Sorting: new (newest first, default) | top (highest score first) | old (oldest first).
     * Authentication is OPTIONAL — unauthenticated callers receive userVote = 0.
     */
    @GetMapping("/api/posts/{postId}/comments")
    public Page<CommentResponse> getComments(
            @PathVariable Long postId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "new") String sort,
            Authentication authentication // nullable — Spring injects null when unauthenticated
    ) {
        String email = authentication != null ? authentication.getName() : null;
        return commentService.getCommentsForPost(postId, email, page, size, sort);
    }

    // ─── PUT /api/comments/{id} ────────────────────────────────────────────────
    /** Edit a comment's text. Only the author can edit; deleted comments cannot be edited. */
    @PutMapping("/api/comments/{id}")
    public CommentResponse updateComment(
            @PathVariable Long id,
            @Valid @RequestBody UpdateCommentRequest request,
            Authentication authentication
    ) {
        return commentService.updateComment(id, authentication.getName(), request);
    }

    // ─── POST /api/comments/{id}/vote ──────────────────────────────────────────
    /** Vote on a comment (+1 / -1). Requires authentication. */
    @PostMapping("/api/comments/{id}/vote")
    public CommentResponse voteComment(
            @PathVariable Long id,
            @RequestBody CommentVoteRequest request,
            Authentication authentication
    ) {
        return commentService.voteComment(id, authentication.getName(), request.toValue());
    }

    // ─── DELETE /api/comments/{id} ─────────────────────────────────────────────
    /** Soft-deletes a comment (only the author can delete). */
    @DeleteMapping("/api/comments/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteComment(
            @PathVariable Long id,
            Authentication authentication
    ) {
        commentService.deleteComment(id, authentication.getName());
    }
}
