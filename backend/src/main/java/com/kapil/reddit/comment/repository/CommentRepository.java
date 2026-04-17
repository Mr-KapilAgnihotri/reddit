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
     * Fetches ALL non-deleted comments for a post in a SINGLE query (used for tree building).
     * Sorted by createdAt ASC — children always appear in chronological order.
     */
    @Query("SELECT c FROM Comment c " +
           "JOIN FETCH c.author " +
           "WHERE c.post.id = :postId " +
           "ORDER BY c.createdAt ASC")
    List<Comment> findAllByPostId(@Param("postId") Long postId);

    // ─── Paginated root-level comment queries (sort variants) ─────────────────

    /** sort=new — newest roots first. Tiebreaker on id DESC for deterministic pagination. */
    @Query("SELECT c FROM Comment c " +
           "JOIN FETCH c.author " +
           "WHERE c.post.id = :postId AND c.parentComment IS NULL " +
           "ORDER BY c.createdAt DESC, c.id DESC")
    Page<Comment> findRootsByPostIdOrderByNew(@Param("postId") Long postId, Pageable pageable);

    /** sort=old — oldest roots first. */
    @Query("SELECT c FROM Comment c " +
           "JOIN FETCH c.author " +
           "WHERE c.post.id = :postId AND c.parentComment IS NULL " +
           "ORDER BY c.createdAt ASC, c.id ASC")
    Page<Comment> findRootsByPostIdOrderByOld(@Param("postId") Long postId, Pageable pageable);

    /**
     * sort=top — highest net score first.
     * Dual-column ORDER BY: score DESC, then createdAt DESC as tiebreaker.
     * This guarantees deterministic, stable pagination when scores are equal.
     */
    @Query("SELECT c FROM Comment c " +
           "JOIN FETCH c.author " +
           "WHERE c.post.id = :postId AND c.parentComment IS NULL " +
           "ORDER BY c.score DESC, c.createdAt DESC")
    Page<Comment> findRootsByPostIdOrderByTop(@Param("postId") Long postId, Pageable pageable);

    // ─── Legacy default (kept for backward compatibility) ─────────────────────
    @Query("SELECT c FROM Comment c " +
           "JOIN FETCH c.author " +
           "WHERE c.post.id = :postId AND c.parentComment IS NULL " +
           "ORDER BY c.createdAt ASC")
    Page<Comment> findRootsByPostId(@Param("postId") Long postId, Pageable pageable);
}

