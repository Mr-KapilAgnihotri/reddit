package com.kapil.reddit.post.event;

import com.kapil.reddit.post.service.PostService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumes post-embedding events from the Python ML service.
 *
 * Flow:
 *  1. ML service generates embedding via SentenceTransformer after moderation.
 *  2. Publishes to Kafka topic: post-embedding.
 *  3. This consumer writes the float[] embedding to posts.embedding via PostService.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EmbeddingConsumer {

    private final PostService postService;

    @KafkaListener(
            topics = "post-embedding",
            groupId = "embedding-consumer-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onEmbeddingReceived(EmbeddingEvent event) {
        if (event == null || event.getPostId() == null || event.getEmbedding() == null) {
            log.warn("EmbeddingConsumer: received null or incomplete event — skipping");
            return;
        }
        log.info("EmbeddingConsumer: storing embedding for postId={} dim={}",
                event.getPostId(), event.getEmbedding().size());
        try {
            postService.storeEmbedding(event);
        } catch (Exception e) {
            log.error("EmbeddingConsumer: failed to store embedding for postId={}: {}",
                    event.getPostId(), e.getMessage(), e);
        }
    }
}
