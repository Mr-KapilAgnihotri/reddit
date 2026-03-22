package com.kapil.reddit.post.vote.service;

import com.kapil.reddit.common.exception.BusinessException;
import com.kapil.reddit.post.domain.Post;
import com.kapil.reddit.post.repository.PostRepository;
import com.kapil.reddit.post.vote.domain.PostVote;
import com.kapil.reddit.post.vote.repository.PostVoteRepository;
import com.kapil.reddit.user.domain.User;
import com.kapil.reddit.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class PostVoteService {

    private final PostVoteRepository postVoteRepository;
    private final PostRepository postRepository;
    private final UserRepository userRepository;

    public void vote(Long postId, String email, short newValue) {

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException("User not found"));

        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new BusinessException("Post not found"));

        PostVote existingVote = postVoteRepository
                .findByPostIdAndUserId(postId, user.getId())
                .orElse(null);

        if (existingVote == null) {

            // NEW VOTE
            PostVote vote = PostVote.builder()
                    .postId(postId)
                    .userId(user.getId())
                    .value(newValue) // +1 or -1
                    .createdAt(Instant.now())
                    .build();

            postVoteRepository.save(vote);

            applyVote(post, newValue, 1);

        } else {

            if (existingVote.getValue() == newValue) {

                // REMOVE VOTE
                postVoteRepository.delete(existingVote);

                applyVote(post, newValue, -1);

            } else {

                // SWITCH VOTE
                short oldValue = existingVote.getValue();

                existingVote.setValue(newValue);
                postVoteRepository.save(existingVote);

                applyVote(post, oldValue, -1);
                applyVote(post, newValue, 1);
            }
        }

        updateScore(post);
        postRepository.save(post);
    }

    private void applyVote(Post post, short value, int delta) {

        if (value == 1) {
            post.setUpvotes(post.getUpvotes() + delta);
        } else {
            post.setDownvotes(post.getDownvotes() + delta);
        }
    }

    private void updateScore(Post post) {
        post.setScore(post.getUpvotes() - post.getDownvotes());
    }
}
