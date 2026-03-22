package com.kapil.reddit.post.repository;

import com.kapil.reddit.post.domain.Post;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PostRepository extends JpaRepository<Post, Long> {

        @EntityGraph(attributePaths = "media")
        Page<Post> findByIsDeletedFalseOrderByCreatedAtDesc(Pageable pageable);

        @EntityGraph(attributePaths = "media")
        Page<Post> findByAuthorIdAndIsDeletedFalseOrderByCreatedAtDesc(
                Long authorId,
                Pageable pageable
        );

        @EntityGraph(attributePaths = "media")
        Page<Post> findByCommunityIdAndIsDeletedFalseOrderByCreatedAtDesc(
                Long communityId,
                Pageable pageable
        );

        @EntityGraph(attributePaths = "media")
        Page<Post> findByCommunityIdInAndIsDeletedFalseOrderByCreatedAtDesc(
                List<Long> communityIds,
                Pageable pageable
        );
    }
