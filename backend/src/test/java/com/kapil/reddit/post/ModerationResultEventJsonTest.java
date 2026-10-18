package com.kapil.reddit.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kapil.reddit.post.event.EmbeddingEvent;
import com.kapil.reddit.post.event.ModerationResultEvent;
import com.kapil.reddit.post.event.PostCreatedEvent;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * JSON contract tests between the Spring Boot backend and the Python ML service.
 *
 * <p>Regression for the bug where Lombok mapped {@code boolean isModerated} to the JSON
 * property {@code "moderated"}, so {@code {"isModerated": true}} from Python was silently
 * dropped and every post stayed {@code moderated=false}.
 */
class ModerationResultEventJsonTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void deserialize_isModeratedTrue_fromPythonPayload() throws Exception {
        String json = """
                {"postId": 7, "maskedText": "what the **** is this", "isModerated": true}
                """;

        ModerationResultEvent event = mapper.readValue(json, ModerationResultEvent.class);

        assertThat(event.getPostId()).isEqualTo(7L);
        assertThat(event.getMaskedText()).isEqualTo("what the **** is this");
        assertThat(event.isModerated()).isTrue();
    }

    @Test
    void deserialize_isModeratedFalse() throws Exception {
        ModerationResultEvent event = mapper.readValue(
                "{\"postId\": 8, \"maskedText\": \"hello\", \"isModerated\": false}",
                ModerationResultEvent.class);

        assertThat(event.isModerated()).isFalse();
    }

    @Test
    void deserialize_fullPayload_withExtraFieldsAndUnknownProperties() throws Exception {
        // Exactly the shape app/consumer.py publishes, plus a field the backend doesn't know.
        String json = """
                {
                  "postId": 9,
                  "maskedText": "**** off",
                  "maskedTitle": "bad ****",
                  "isModerated": true,
                  "categories": ["profanity", "harassment"],
                  "severity": 0.8,
                  "reason": "contains profanity",
                  "provider": "better_profanity",
                  "someFutureField": {"nested": 1}
                }
                """;

        ModerationResultEvent event = mapper.readValue(json, ModerationResultEvent.class);

        assertThat(event.getPostId()).isEqualTo(9L);
        assertThat(event.isModerated()).isTrue();
        assertThat(event.getMaskedTitle()).isEqualTo("bad ****");
        assertThat(event.getCategories()).containsExactly("profanity", "harassment");
        assertThat(event.getSeverity()).isEqualTo(0.8);
        assertThat(event.getReason()).isEqualTo("contains profanity");
        assertThat(event.getProvider()).isEqualTo("better_profanity");
    }

    @Test
    void deserialize_missingOptionalFields_defaults() throws Exception {
        ModerationResultEvent event = mapper.readValue("{\"postId\": 10}", ModerationResultEvent.class);

        assertThat(event.isModerated()).isFalse();
        assertThat(event.getMaskedText()).isNull();
        assertThat(event.getMaskedTitle()).isNull();
        assertThat(event.getCategories()).isEmpty();
    }

    @Test
    void serialize_usesIsModeratedPropertyName_andRoundTrips() throws Exception {
        ModerationResultEvent event = new ModerationResultEvent(11L, "****", true);

        String json = mapper.writeValueAsString(event);
        JsonNode node = mapper.readTree(json);

        assertThat(node.has("isModerated")).isTrue();
        assertThat(node.get("isModerated").asBoolean()).isTrue();
        assertThat(node.has("moderated")).isFalse();
        assertThat(mapper.readValue(json, ModerationResultEvent.class).isModerated()).isTrue();
    }

    @Test
    void postCreatedEvent_serializesFieldsTheMlServiceReads() throws Exception {
        JsonNode node = mapper.readTree(mapper.writeValueAsString(
                new PostCreatedEvent(5L, "body", "POST", "title")));

        assertThat(node.get("postId").asLong()).isEqualTo(5L);
        assertThat(node.get("text").asText()).isEqualTo("body");
        assertThat(node.get("type").asText()).isEqualTo("POST");
        assertThat(node.get("title").asText()).isEqualTo("title");
    }

    @Test
    void embeddingEvent_deserializesFloatList() throws Exception {
        EmbeddingEvent event = mapper.readValue(
                "{\"postId\": 3, \"embedding\": [0.1, -0.25, 1]}", EmbeddingEvent.class);

        assertThat(event.getPostId()).isEqualTo(3L);
        assertThat(event.getEmbedding()).containsExactly(0.1f, -0.25f, 1f);
    }
}
