package com.kapil.reddit.post.vote.repository;

import com.kapil.reddit.post.vote.domain.PostVote;
import com.kapil.reddit.post.vote.domain.PostVoteId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PostVoteRepository extends JpaRepository<PostVote, PostVoteId> {

    Optional<PostVote> findByPostIdAndUserId(Long postId, Long userId);
}
