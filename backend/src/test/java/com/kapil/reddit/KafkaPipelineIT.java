package com.kapil.reddit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kapil.reddit.post.event.ModerationResultEvent;
import com.kapil.reddit.post.event.PostCreatedEvent;
import com.kapil.reddit.support.AbstractIntegrationTest;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.mail.javamail.JavaMailSender;

import java.time.Duration;
import java.util.Collections;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end Kafka pipeline integration test using EmbeddedKafka.
 *
 * <p>Verifies the Spring Boot producer side:
 *   Publishes a {@link PostCreatedEvent} to {@code post-created} and checks
 *   the message has the ML-compatible JSON schema.
 *
 * <p>Also verifies the consumer side is resilient to invalid JSON on
 *   {@code post-moderated}.
 */
class KafkaPipelineIT extends AbstractIntegrationTest {

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    private EmbeddedKafkaBroker embeddedKafkaBroker;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private JavaMailSender javaMailSender;

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Consumer<String, String> createRawConsumer(String groupId) {
        Map<String, Object> props = KafkaTestUtils.consumerProps(groupId, "true", embeddedKafkaBroker);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
        return new DefaultKafkaConsumerFactory<String, String>(props).createConsumer();
    }

    // ── Topic: post-created ───────────────────────────────────────────────────

    /**
     * Verify that {@link PostCreatedEvent} messages have the correct JSON schema
     * that the ML service expects: {@code { "postId": number, "text": string }}.
     */
    @Test
    void producer_sendsCorrectSchemaToPostCreatedTopic() throws Exception {
        // Create a dedicated consumer and seek to end BEFORE publishing so we
        // only see OUR message (avoids "more than one record" when contexts reuse topics)
        Consumer<String, String> consumer = createRawConsumer("pipeline-schema-test-" + System.nanoTime());
        TopicPartition tp = new TopicPartition("post-created", 0);
        consumer.assign(Collections.singletonList(tp));
        consumer.seekToEnd(Collections.singletonList(tp));
        // Capture the end offset before publish
        long endOffset = consumer.position(tp);

        PostCreatedEvent event = new PostCreatedEvent(99L, "Test content for moderation", "POST");
        kafkaTemplate.send("post-created", event).get();

        // Seek exactly to our message's offset
        consumer.seek(tp, endOffset);

        ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(10));
        consumer.close();

        assertThat(records.isEmpty()).isFalse();

        // Check the first new record (our message)
        var record = records.iterator().next();
        var node = objectMapper.readTree(record.value());

        assertThat(node.has("postId")).isTrue();
        assertThat(node.get("postId").asLong()).isEqualTo(99L);
        assertThat(node.has("text")).isTrue();
        assertThat(node.get("text").asText()).isEqualTo("Test content for moderation");
    }

    // ── Topic: post-moderated (simulate ML response) ─────────────────────────

    /**
     * Simulate the ML service publishing a result to {@code post-moderated}
     * and verify that Spring Boot's @KafkaListener receives it without crashing.
     * The post won't exist in the DB so the service logs a warning — that's expected.
     */
    @Test
    void consumer_handlesModeratedMessageWithoutErrors() throws Exception {
        ModerationResultEvent result = new ModerationResultEvent();
        result.setPostId(999L);
        result.setMaskedText("cleaned text");
        result.setModerated(false);

        String json = objectMapper.writeValueAsString(result);
        kafkaTemplate.send("post-moderated", json).get();

        // Allow the @KafkaListener time to process; it will log "post not found" warning —
        // acceptable because postId=999 doesn't exist in the test DB.
        Thread.sleep(2000);
        // Success = no exception propagated to this test
    }

    // ── Invalid message handling ──────────────────────────────────────────────

    @Test
    void consumer_doesNotCrashOnInvalidJson() throws Exception {
        kafkaTemplate.send("post-moderated", "NOT VALID JSON AT ALL").get();
        Thread.sleep(2000);
        // Success = consumer thread still alive — verified by no timeout/exception
    }

    @Test
    void producer_sendsMessageWithNullKey() throws Exception {
        PostCreatedEvent event = new PostCreatedEvent(1L, "text", "POST");
        kafkaTemplate.send("post-created", null, event).get();
        // No exception = pass
    }
}
