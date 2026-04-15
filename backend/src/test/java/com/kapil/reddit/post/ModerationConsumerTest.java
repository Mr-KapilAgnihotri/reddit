package com.kapil.reddit.post;

import com.kapil.reddit.post.event.ModerationConsumer;
import com.kapil.reddit.post.event.ModerationResultEvent;
import com.kapil.reddit.post.service.PostService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link ModerationConsumer}.
 *
 * Pure Mockito — no Spring context, no Kafka broker required.
 */
@ExtendWith(MockitoExtension.class)
class ModerationConsumerTest {

    @Mock
    private PostService postService;

    @InjectMocks
    private ModerationConsumer moderationConsumer;

    private ModerationResultEvent buildEvent(Long postId, String maskedText, boolean isModerated) {
        ModerationResultEvent event = new ModerationResultEvent();
        event.setPostId(postId);
        event.setMaskedText(maskedText);
        event.setModerated(isModerated);
        return event;
    }

    // ── Happy path ────────────────────────────────────────────────────────────

    @Test
    void consume_validEvent_delegatesToPostService() {
        ModerationResultEvent event = buildEvent(1L, "Hello ****!", true);

        moderationConsumer.consume(event, "post-moderated", 0L);

        verify(postService).handleModerationResult(event);
    }

    @Test
    void consume_cleanPost_delegatesToPostService() {
        ModerationResultEvent event = buildEvent(2L, "Nice clean text.", false);

        moderationConsumer.consume(event, "post-moderated", 1L);

        verify(postService).handleModerationResult(event);
    }

    // ── Null / bad payload ────────────────────────────────────────────────────

    @Test
    void consume_nullEvent_doesNotCallPostService() {
        // Null payload = ErrorHandlingDeserializer couldn't parse the message
        moderationConsumer.consume(null, "post-moderated", 2L);

        verifyNoInteractions(postService);
    }

    // ── Exception safety ──────────────────────────────────────────────────────

    @Test
    void consume_serviceThrows_doesNotPropagateException() {
        ModerationResultEvent event = buildEvent(99L, "bad", false);
        doThrow(new RuntimeException("DB error")).when(postService).handleModerationResult(event);

        // Must NOT throw — consumer loop must survive service failures
        moderationConsumer.consume(event, "post-moderated", 3L);

        verify(postService).handleModerationResult(event);
    }

    @Test
    void consume_multipleMessages_processedIndependently() {
        ModerationResultEvent e1 = buildEvent(10L, "text1", false);
        ModerationResultEvent e2 = buildEvent(11L, "text2", true);

        moderationConsumer.consume(e1, "post-moderated", 4L);
        moderationConsumer.consume(e2, "post-moderated", 5L);

        verify(postService).handleModerationResult(e1);
        verify(postService).handleModerationResult(e2);
    }
}
