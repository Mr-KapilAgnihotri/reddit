package com.kapil.reddit.post;

import com.kapil.reddit.post.event.EmbeddingConsumer;
import com.kapil.reddit.post.event.EmbeddingEvent;
import com.kapil.reddit.post.service.PostService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link EmbeddingConsumer}.
 *
 * Pure Mockito — no Spring context, no Kafka broker required.
 */
@ExtendWith(MockitoExtension.class)
class EmbeddingConsumerTest {

    @Mock
    private PostService postService;

    @InjectMocks
    private EmbeddingConsumer embeddingConsumer;

    private EmbeddingEvent buildEvent(Long postId, List<Float> embedding) {
        EmbeddingEvent event = new EmbeddingEvent();
        event.setPostId(postId);
        event.setEmbedding(embedding);
        return event;
    }

    @Test
    void validEvent_delegatesToPostService() {
        EmbeddingEvent event = buildEvent(1L, List.of(0.1f, 0.2f));

        embeddingConsumer.onEmbeddingReceived(event);

        verify(postService).storeEmbedding(event);
    }

    @Test
    void nullEvent_isSkipped() {
        // Null payload = ErrorHandlingDeserializer couldn't parse the message
        embeddingConsumer.onEmbeddingReceived(null);

        verifyNoInteractions(postService);
    }

    @Test
    void nullPostId_isSkipped() {
        embeddingConsumer.onEmbeddingReceived(buildEvent(null, List.of(0.1f)));

        verifyNoInteractions(postService);
    }

    @Test
    void nullEmbedding_isSkipped() {
        embeddingConsumer.onEmbeddingReceived(buildEvent(1L, null));

        verifyNoInteractions(postService);
    }

    @Test
    void serviceThrows_doesNotPropagateException() {
        EmbeddingEvent event = buildEvent(2L, List.of(0.5f));
        doThrow(new RuntimeException("DB error")).when(postService).storeEmbedding(event);

        // Must NOT throw — the listener container must keep consuming
        embeddingConsumer.onEmbeddingReceived(event);

        verify(postService).storeEmbedding(event);
    }
}
