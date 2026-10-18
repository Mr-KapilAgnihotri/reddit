package com.kapil.reddit.post.event;

import com.kapil.reddit.post.domain.Post;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class PostProducer {

    public static final String POST_CREATED_TOPIC = "post-created";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void sendPostCreatedEvent(Post post) {
        PostCreatedEvent event = new PostCreatedEvent(
                post.getId(),
                post.getOriginalText(),
                "POST",
                post.getTitle()
        );

        // Two-arg send kept on purpose (PostProducerTest verifies this signature).
        kafkaTemplate.send(POST_CREATED_TOPIC, event);
        log.info("Published post-created | postId={}", post.getId());
    }
}
