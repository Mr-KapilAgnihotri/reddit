package com.kapil.reddit.comment.repository;

import com.kapil.reddit.comment.domain.CommentVote;
import com.kapil.reddit.comment.domain.CommentVoteId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CommentVoteRepository extends JpaRepository<CommentVote, CommentVoteId> {

    Optional<CommentVote> findByCommentIdAndUserId(Long commentId, Long userId);

    /**
     * Batch-fetches all votes cast by userId for the given comment IDs.
     * Called once per getComments request to populate userVote for the entire
     * tree — prevents N+1 vote queries.
     */
    List<CommentVote> findByUserIdAndCommentIdIn(Long userId, List<Long> commentIds);
}
