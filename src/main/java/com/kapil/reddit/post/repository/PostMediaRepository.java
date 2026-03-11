package com.kapil.reddit.post.repository;

import com.kapil.reddit.post.domain.PostMedia;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PostMediaRepository extends JpaRepository<PostMedia, Long> {

    List<PostMedia> findByPostId(Long postId);
}
