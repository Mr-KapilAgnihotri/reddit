package com.kapil.reddit.post.repository;

import com.kapil.reddit.post.domain.Post;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PostRepository extends JpaRepository<Post, Long> {

    List<Post> findByCommunityIdAndIsDeletedFalseOrderByCreatedAtDesc(Long communityId);
}
