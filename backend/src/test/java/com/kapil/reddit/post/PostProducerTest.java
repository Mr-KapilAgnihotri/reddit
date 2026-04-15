package com.kapil.reddit.post;

import com.kapil.reddit.post.domain.Post;
import com.kapil.reddit.post.event.PostCreatedEvent;
import com.kapil.reddit.post.event.PostProducer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for {@link PostProducer}.
 *
 * Uses Mockito only — no Spring context, no Kafka broker required.
 */
@ExtendWith(MockitoExtension.class)
class PostProducerTest {

    @Mock
    @SuppressWarnings("rawtypes")
    private KafkaTemplate kafkaTemplate;

    @InjectMocks
    private PostProducer postProducer;

    private Post buildPost(Long id, String text) {
        Post post = new Post();
        post.setId(id);
        post.setOriginalText(text);
        return post;
    }

    @Test
    void sendPostCreatedEvent_sendsToCorrectTopic() {
        Post post = buildPost(1L, "Hello world!");

        postProducer.sendPostCreatedEvent(post);

        //noinspection unchecked
        verify(kafkaTemplate).send(eq("post-created"), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void sendPostCreatedEvent_payloadContainsPostIdAndText() {
        Post post = buildPost(42L, "This is my post content");

        postProducer.sendPostCreatedEvent(post);

        ArgumentCaptor<PostCreatedEvent> captor = ArgumentCaptor.forClass(PostCreatedEvent.class);
        //noinspection unchecked
        verify(kafkaTemplate).send(eq("post-created"), captor.capture());

        PostCreatedEvent event = captor.getValue();
        assertThat(event.getPostId()).isEqualTo(42L);
        assertThat(event.getText()).isEqualTo("This is my post content");
    }

    @Test
    void sendPostCreatedEvent_nullTextDoesNotThrow() {
        Post post = buildPost(5L, null);

        // Should complete without exception even when text is null
        postProducer.sendPostCreatedEvent(post);

        //noinspection unchecked
        verify(kafkaTemplate).send(eq("post-created"), org.mockito.ArgumentMatchers.any());
    }
}
