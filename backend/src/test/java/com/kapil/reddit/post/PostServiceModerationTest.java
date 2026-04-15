package com.kapil.reddit.post;

import com.kapil.reddit.post.domain.Post;
import com.kapil.reddit.post.event.ModerationResultEvent;
import com.kapil.reddit.post.event.PostProducer;
import com.kapil.reddit.post.repository.PostMediaRepository;
import com.kapil.reddit.post.repository.PostRepository;
import com.kapil.reddit.post.service.PostService;
import com.kapil.reddit.post.vote.repository.PostVoteRepository;
import com.kapil.reddit.community.repository.CommunityMemberRepository;
import com.kapil.reddit.community.repository.CommunityRepository;
import com.kapil.reddit.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for the moderation-related methods in {@link PostService}.
 *
 * Pure Mockito; no Spring context required.
 */
@ExtendWith(MockitoExtension.class)
class PostServiceModerationTest {

    @Mock private PostRepository postRepository;
    @Mock private PostMediaRepository postMediaRepository;
    @Mock private CommunityRepository communityRepository;
    @Mock private CommunityMemberRepository communityMemberRepository;
    @Mock private UserRepository userRepository;
    @Mock private PostVoteRepository postVoteRepository;
    @Mock private PostProducer postProducer;

    @InjectMocks
    private PostService postService;

    private Post existingPost;

    @BeforeEach
    void setUp() {
        existingPost = new Post();
        existingPost.setId(1L);
        existingPost.setOriginalText("What the heck is this?");
        existingPost.setDisplayText("What the heck is this?");
        existingPost.setIsModerated(false);
        existingPost.setCreatedAt(Instant.now());
        existingPost.setUpdatedAt(Instant.now());
    }

    private ModerationResultEvent buildEvent(Long postId, String masked, boolean moderated) {
        ModerationResultEvent e = new ModerationResultEvent();
        e.setPostId(postId);
        e.setMaskedText(masked);
        e.setModerated(moderated);
        return e;
    }

    // ── handleModerationResult — happy path ──────────────────────────────────

    @Test
    void handleModerationResult_updatesDisplayText() {
        when(postRepository.findById(1L)).thenReturn(Optional.of(existingPost));
        when(postRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        postService.handleModerationResult(buildEvent(1L, "What the **** is this?", true));

        ArgumentCaptor<Post> captor = ArgumentCaptor.forClass(Post.class);
        verify(postRepository).save(captor.capture());
        assertThat(captor.getValue().getDisplayText()).isEqualTo("What the **** is this?");
    }

    @Test
    void handleModerationResult_setsIsModeratedTrue() {
        when(postRepository.findById(1L)).thenReturn(Optional.of(existingPost));
        when(postRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        postService.handleModerationResult(buildEvent(1L, "****", true));

        ArgumentCaptor<Post> captor = ArgumentCaptor.forClass(Post.class);
        verify(postRepository).save(captor.capture());
        assertThat(captor.getValue().getIsModerated()).isTrue();
    }

    @Test
    void handleModerationResult_cleanPost_isModeratedFalse() {
        when(postRepository.findById(1L)).thenReturn(Optional.of(existingPost));
        when(postRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        postService.handleModerationResult(buildEvent(1L, "Hello world", false));

        ArgumentCaptor<Post> captor = ArgumentCaptor.forClass(Post.class);
        verify(postRepository).save(captor.capture());
        assertThat(captor.getValue().getIsModerated()).isFalse();
        assertThat(captor.getValue().getDisplayText()).isEqualTo("Hello world");
    }

    @Test
    void handleModerationResult_updatesUpdatedAt() {
        Instant before = Instant.now().minusSeconds(60);
        existingPost.setUpdatedAt(before);
        when(postRepository.findById(1L)).thenReturn(Optional.of(existingPost));
        when(postRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        postService.handleModerationResult(buildEvent(1L, "text", false));

        ArgumentCaptor<Post> captor = ArgumentCaptor.forClass(Post.class);
        verify(postRepository).save(captor.capture());
        assertThat(captor.getValue().getUpdatedAt()).isAfter(before);
    }

    // ── handleModerationResult — resilience ──────────────────────────────────

    @Test
    void handleModerationResult_postNotFound_doesNotThrow() {
        when(postRepository.findById(999L)).thenReturn(Optional.empty());

        postService.handleModerationResult(buildEvent(999L, "text", false));

        verify(postRepository, never()).save(any());
    }

    @Test
    void handleModerationResult_nullEvent_doesNotThrow() {
        postService.handleModerationResult(null);
        verifyNoInteractions(postRepository);
    }

    @Test
    void handleModerationResult_nullPostId_doesNotThrow() {
        ModerationResultEvent event = new ModerationResultEvent();
        event.setPostId(null);

        postService.handleModerationResult(event);
        verifyNoInteractions(postRepository);
    }

    @Test
    void handleModerationResult_nullMaskedText_doesNotOverwriteDisplayText() {
        existingPost.setDisplayText("original display");
        when(postRepository.findById(1L)).thenReturn(Optional.of(existingPost));
        when(postRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ModerationResultEvent event = buildEvent(1L, null, true);
        postService.handleModerationResult(event);

        ArgumentCaptor<Post> captor = ArgumentCaptor.forClass(Post.class);
        verify(postRepository).save(captor.capture());
        // displayText must not be overwritten with null
        assertThat(captor.getValue().getDisplayText()).isEqualTo("original display");
        assertThat(captor.getValue().getIsModerated()).isTrue();
    }

    @Test
    void handleModerationResult_isIdempotent() {
        when(postRepository.findById(1L)).thenReturn(Optional.of(existingPost));
        when(postRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ModerationResultEvent event = buildEvent(1L, "****", true);

        // Call twice — should not throw and should save twice (idempotent update)
        postService.handleModerationResult(event);
        postService.handleModerationResult(event);

        verify(postRepository, times(2)).save(any());
    }
}
