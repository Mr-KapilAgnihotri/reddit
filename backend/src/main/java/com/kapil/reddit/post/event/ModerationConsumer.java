package com.kapil.reddit.post.event;

import com.kapil.reddit.post.service.PostService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/**
 * Kafka consumer that listens on the {@code post-moderated} topic.
 *
 * <p>Published by the Python ML service after it has filtered the post text.
 * This consumer persists the moderation result to the database via
 * {@link PostService#handleModerationResult(ModerationResultEvent)}.
 *
 * <h3>Resilience guarantees</h3>
 * <ul>
 *   <li>Null payloads (caused by {@code ErrorHandlingDeserializer} on corrupt JSON)
 *       are detected and skipped — the offset is committed so the bad message
 *       does not block processing.</li>
 *   <li>Any exception thrown by the service layer is caught and logged;
 *       the consumer continues with the next message.</li>
 *   <li>Processing is idempotent: if the same {@code postId} is received
 *       twice the post is simply updated again with the same values.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ModerationConsumer {

    private final PostService postService;

    @KafkaListener(
            topics = "post-moderated",
            groupId = "${spring.kafka.consumer.group-id:moderation-group}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(
            @Payload(required = false) ModerationResultEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.OFFSET) long offset
    ) {
        if (event == null) {
            log.warn("Received null/undeserializable message on topic='{}' offset={} — skipping", topic, offset);
            return;
        }

        log.info("📩 ModerationConsumer | topic={} offset={} | postId={} isModerated={}",
                topic, offset, event.getPostId(), event.isModerated());

        try {
            postService.handleModerationResult(event);
            log.info("✅ Moderation result persisted for postId={}", event.getPostId());
        } catch (Exception ex) {
            // Log and skip — never let an exception propagate back to Kafka
            // (that would trigger infinite retries / poison-pill loops).
            log.error("Failed to persist moderation result for postId={}: {}",
                    event.getPostId(), ex.getMessage(), ex);
        }
    }
}
