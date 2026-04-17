package com.kapil.reddit.post.repository;

import com.kapil.reddit.post.domain.SavedPost;
import com.kapil.reddit.post.domain.SavedPostId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SavedPostRepository extends JpaRepository<SavedPost, SavedPostId> {

    boolean existsByIdUserIdAndIdPostId(Long userId, Long postId);

    void deleteByIdUserIdAndIdPostId(Long userId, Long postId);

    /**
     * Fetch saved posts for a user with a single JOIN FETCH to avoid N+1.
     * Eagerly loads Post → community and Post → author in one query per batch.
     */
    @Query("""
            SELECT sp FROM SavedPost sp
            JOIN FETCH sp.post p
            LEFT JOIN FETCH p.community
            JOIN FETCH p.author
            WHERE sp.user.id = :userId
              AND p.isDeleted = false
            ORDER BY sp.savedAt DESC
            """)
    List<SavedPost> findByUserIdWithPost(@Param("userId") Long userId);

    /**
     * Returns just the post IDs for a user — used by recommendation exclusions.
     */
    @Query("SELECT sp.id.postId FROM SavedPost sp WHERE sp.id.userId = :userId")
    List<Long> findPostIdsByUserId(@Param("userId") Long userId);
}
