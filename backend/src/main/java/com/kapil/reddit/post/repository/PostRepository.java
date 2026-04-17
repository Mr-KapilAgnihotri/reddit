package com.kapil.reddit.post.repository;

import com.kapil.reddit.post.domain.Post;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PostRepository extends JpaRepository<Post, Long> {

        @EntityGraph(attributePaths = "media")
        Page<Post> findByIsDeletedFalse(Pageable pageable);

        @EntityGraph(attributePaths = "media")
        Page<Post> findByAuthorIdAndIsDeletedFalse(
                Long authorId,
                Pageable pageable
        );

        @EntityGraph(attributePaths = "media")
        Page<Post> findByCommunityIdAndIsDeletedFalse(
                Long communityId,
                Pageable pageable
        );

        @EntityGraph(attributePaths = "media")
        Page<Post> findByCommunityIdInAndIsDeletedFalse(
                List<Long> communityIds,
                Pageable pageable
        );

        // ─── PGVector recommendation query ────────────────────────────────────────

        /**
         * Cosine similarity search using the pgvector {@code <=>} operator.
         * Returns posts ordered by proximity to the supplied query embedding,
         * excluding posts the user has already authored, upvoted, or saved
         * (passed in as {@code excludedPostIds}).
         *
         * The CAST to ::vector is required because JDBC sends the float array as a
         * JDBC Array; pgvector's <=> operator expects the native vector type.
         *
         * @param queryVector     float array of length 384 (average of reference embeddings)
         * @param excludedPostIds posts to hide (authored, upvoted, saved)
         * @param limit           max number of results to return
         */
        @Query(value = """
                SELECT p.* FROM posts p
                WHERE p.is_deleted = false
                  AND p.embedding IS NOT NULL
                  AND (:excludedCount = 0 OR p.id NOT IN (:excludedPostIds))
                ORDER BY p.embedding <=> CAST(:queryVector AS vector)
                LIMIT :limit
                """, nativeQuery = true)
        List<Post> findSimilarPosts(
                @Param("queryVector")    String  queryVector,
                @Param("excludedPostIds") List<Long> excludedPostIds,
                @Param("excludedCount") int excludedCount,
                @Param("limit")          int     limit
        );

        /**
         * Returns post IDs upvoted by the given user, for building the exclusion list
         * and the average query embedding.
         */
        @Query("""
                SELECT pv.postId FROM PostVote pv
                WHERE pv.userId = :userId
                  AND pv.value = 1
                ORDER BY pv.createdAt DESC
                """)
        List<Long> findUpvotedPostIdsByUserId(@Param("userId") Long userId);

        /** Fetch posts by their IDs (used to gather reference embeddings). */
        @Query("SELECT p FROM Post p WHERE p.id IN :ids AND p.embedding IS NOT NULL")
        List<Post> findByIdInWithEmbedding(@Param("ids") List<Long> ids);
}

