package com.kapil.reddit.comment.repository;

import com.kapil.reddit.comment.domain.Comment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CommentRepository extends JpaRepository<Comment, Long> {

    /**
     * Fetches ALL non-deleted comments for a post in a SINGLE query.
     * This is intentional — results are grouped into a tree in memory,
     * preventing N+1 database calls regardless of nesting depth.
     * Sorted by createdAt ASC for deterministic Reddit-style ordering.
     */
    @Query("SELECT c FROM Comment c " +
           "JOIN FETCH c.author " +
           "WHERE c.post.id = :postId " +
           "ORDER BY c.createdAt ASC")
    List<Comment> findAllByPostId(@Param("postId") Long postId);

    /**
     * Paginated root-level comments only (no parent).
     * Used to determine which roots to include in the response page,
     * while all children are fetched via findAllByPostId.
     * Deleted roots are included so their children remain visible.
     */
    @Query("SELECT c FROM Comment c " +
           "JOIN FETCH c.author " +
           "WHERE c.post.id = :postId AND c.parentComment IS NULL " +
           "ORDER BY c.createdAt ASC")
    Page<Comment> findRootsByPostId(@Param("postId") Long postId, Pageable pageable);
}
