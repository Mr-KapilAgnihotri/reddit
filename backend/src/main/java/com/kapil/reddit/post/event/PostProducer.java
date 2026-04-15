package com.kapil.reddit.post.event;

import com.kapil.reddit.post.domain.Post;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PostProducer {
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void sendPostCreatedEvent(Post post) {
        PostCreatedEvent event = new PostCreatedEvent(
                post.getId(),
                post.getOriginalText(),
                "POST"
        );

        kafkaTemplate.send("post-created", event);
    }
}
